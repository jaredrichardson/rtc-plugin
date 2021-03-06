package com.deluan.jenkins.plugins.rtc.commands;

import com.deluan.jenkins.plugins.rtc.JazzConfiguration;
import com.deluan.jenkins.plugins.rtc.changelog.JazzChangeSet;
import com.deluan.jenkins.plugins.rtc.commands.accept.*;
import hudson.util.ArgumentListBuilder;
import hudson.model.TaskListener;
import hudson.FilePath;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.text.ParseException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;

/**
 * @author deluan
 */
public class AcceptCommand extends AbstractCommand implements ParseableCommand<Map<String, JazzChangeSet>> {

    public static final String FORMAT_VERSION_2_1_0 = "2.1.0";
    public static final String FORMAT_VERSION_3_1_0 = "3.1.0";
    private Collection<String> changeSets;
    private boolean useJson;
    private BaseAcceptOutputParser parser;
    protected boolean oldFormat = false;
	public TaskListener listener;
	private String jazzExecutable = null;

	public AcceptCommand(JazzConfiguration configurationProvider,
    						Collection<String> changeSets,
    						String version) {
		this(configurationProvider, changeSets, version, null, null);
    }

    public AcceptCommand(JazzConfiguration configurationProvider,
    						Collection<String> changeSets,
    						String version, TaskListener listener, String jazzExecutable) {
        super(configurationProvider);

        this.changeSets = new LinkedHashSet<String>(changeSets);
        this.useJson = version.equals("3.1.0-json"); // TODO: Obviously only for testing.

        if (version.compareTo(FORMAT_VERSION_3_1_0) >= 0) {
            parser = useJson ? new JsonAcceptOutputParser() : new AcceptOutputParser_3_1_0();
        } else {
            this.oldFormat = (version.compareTo(FORMAT_VERSION_2_1_0) < 0);
            parser = (oldFormat) ? new AcceptOldOutputParser() : new AcceptNewOutputParser();
        }
    }


    public ArgumentListBuilder getArguments() {
        ArgumentListBuilder args = new ArgumentListBuilder();

        args.add("accept");
        addLoginArgument(args);
        addLocalWorkspaceArgument(args);
        addSourceStream(args);
        args.add("--flow-components", "-o", "-v");
        if (useJson) args.add("--json");

        if (hasAnyChangeSets()) {
            addChangeSets(args);
        }
			args.add("--flow-components", "-o", "-v");
			addRepositoryArgument(args);
		} else { // Use load rules.
			if (output != null) {
				output.println("     -- Using Load Rules...[");
				output.println(sLoadRules);
				output.println("     ]");
			}
	    	args = processLoadRules(sLoadRules);
		}

        return args;
    }

	// Process the load rules.
	public ArgumentListBuilder processLoadRules(String sLoadRules) {
		getConfig().consoleOut("-------------------------------");
		getConfig().consoleOut("-- process Load Rules - START --");
		getConfig().consoleOut("-------------------------------");
		String sUsageString = "Usage: [Component]:[Subfolder Path]";

		FilePath file = getConfig().getBuild().getWorkspace();

		// Process load rules if they exist.
		if (sLoadRules != null && sLoadRules.isEmpty() == false) {
			getConfig().consoleOut("sLoadRules: [" + sLoadRules + "]");

			// Split load rules into a string array.
			String[] aLoadRuleLines = sLoadRules.split("\n");

			int iLoadRuleLines_len = aLoadRuleLines.length;

			String commandData = "";
			///////////////////
			// Loop through the load rule lines...verify and process.
			///////////////////
			for (int iCount = 1;iCount <= iLoadRuleLines_len; iCount++) {
				// Get a line from the array
				String sLine = aLoadRuleLines[iCount-1];

				// Verify the sytax is correct.
				// Line must contain a single ":"
				int iColon1 = sLine.indexOf(":");	// This must exist.
				int iColon2 = sLine.indexOf(":",iColon1+1);  // This should not exist.

				// Check for validity of load rule line
				if (iColon1 == -1 || iColon2 != -1) {
					// INVALID
					getConfig().consoleOut("   *** Load Rule syntax error ***");
					getConfig().consoleOut("       Line:[" + sLine + "] must contain 1 and only 1 ':' character ***");
					getConfig().consoleOut("       " + sUsageString);
				} else {
					// OK
					// Split line into 2 pieces by the ":"
					String[] RulePieces = sLine.split(":");
					String sComponent = RulePieces[0];
					String sFolder = RulePieces[1];

					getConfig().consoleOut("   Component: [" + sComponent + "]");
					getConfig().consoleOut("   Folder: [" + sFolder + "]");

					String sFileName = getConfig().getJobName() + iCount + ".txt";
					String sFileData = "RootFolderName=" + sFolder;
					getConfig().consoleOut("   Writing to file: [" + sFileName + "]");
					getConfig().consoleOut("   Data: [" + sFileData + "]");

					try {
						file.act(new LoadCommand.RemoteFileWriter(file.getRemote() + "\\" + sFileName, sFileData));
					} catch (Exception e) {
						e.printStackTrace();
						getConfig().consoleOut("exception: " + e);
						getConfig().consoleOut("Caused by: " + e.getCause());
					}

					if(sFolder.startsWith("/")) {
						sFolder = sFolder.substring(1, sFolder.length());
					}
					//commandData += jazzExecutable + " load -L " + "\"" + file.getRemote() + "\\" + sFileName + "\" " + getConfig().getWorkspaceName() + " -r " + getConfig().getRepositoryLocation() + " -u %1 -P %2 -d " + "\"" + file.getRemote() + "\\" + sFolder + "\" " + sComponent + "\r\n";
					commandData += jazzExecutable + " accept -r " + getConfig().getRepositoryLocation() + " -u %1 -P %2 -d " + "\"" + file.getRemote() + "\\" + sFolder + "\"\r\n";
				}
			}

			try {
				file.act(new LoadCommand.RemoteFileWriter(file.getRemote() + "\\" + getConfig().getJobName() + ".bat", "@echo off\n" + commandData));
			} catch (Exception e) {
				e.printStackTrace();
				getConfig().consoleOut("exception: " + e);
				getConfig().consoleOut("Caused by: " + e.getCause());
			}

		} else {
			getConfig().consoleOut("");
			getConfig().consoleOut("No load rules found - OK.");
			getConfig().consoleOut("");
		}

		getConfig().consoleOut("-------------------------------");
		getConfig().consoleOut("-- process Load Rules - END --");
		getConfig().consoleOut("-------------------------------");

		ArgumentListBuilder args = new ArgumentListBuilder();
		args.add("cmd");
		args.add("/c");
		args.add("\"" + file.getRemote() + "\\" + getConfig().getJobName() + ".bat\"");
        args.addMasked(getConfig().getUsername());
        args.addMasked(getConfig().getPassword());
		return args;
	}


    public Map<String, JazzChangeSet> parse(BufferedReader reader) throws ParseException, IOException {
        return parser.parse(reader);
    }
}
