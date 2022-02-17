package misc

import "github.com/hashgraph/hedera-sdk-go/v2"

func CallContractVia(
	client *hedera.Client,
	target hedera.ContractID,
	method string,
	params *hedera.ContractFunctionParameters,
) hedera.TransactionRecord {
	txnId, err := hedera.NewContractExecuteTransaction().
		SetContractID(target).
		SetGas(4_000_000).
		SetFunction(method, params).
		Execute(client)
	if err != nil {
		panic(err)
	}

	var record hedera.TransactionRecord

	record, err = hedera.NewTransactionRecordQuery().
		SetTransactionID(txnId.TransactionID).
		SetIncludeChildren(true).
		Execute(client)
	if err != nil {
		panic(err)
	}

	return record
}
