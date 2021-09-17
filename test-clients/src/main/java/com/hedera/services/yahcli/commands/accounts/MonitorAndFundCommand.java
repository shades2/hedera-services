package com.hedera.services.yahcli.commands.accounts;

import com.hedera.services.yahcli.config.ConfigManager;
import com.hedera.services.yahcli.suites.AuctionMonitorSuite;
import picocli.CommandLine;

import java.util.concurrent.Callable;

import static com.hedera.services.yahcli.output.CommonMessages.COMMON_MESSAGES;

@CommandLine.Command(
		name = "monitorAndFund",
		subcommands = { CommandLine.HelpCommand.class },
		description = "Monitor account balances and fund them if below a threshold")
public class MonitorAndFundCommand  implements Callable<Integer> {

	@CommandLine.ParentCommand
	AccountsCommand accountsCommand;

	@Override
	public Integer call() throws Exception {
		var config = ConfigManager.from(accountsCommand.getYahcli());
		config.assertNoMissingDefaults();
		COMMON_MESSAGES.printGlobalInfo(config);

		var delegate = new AuctionMonitorSuite(config.asSpecConfig());
		delegate.runSuiteSync();

		return 0;
	}
}
