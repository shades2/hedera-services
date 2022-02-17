package misc

import (
	"encoding/binary"
	"encoding/json"
	"fmt"
	"github.com/hashgraph/hedera-sdk-go/v2"
	"io/ioutil"
	"strings"
)

const poolFee = 500
const initSqrtPriceX96 = 1
const initTokenSupply = 1_000_000_000
const initLpTokenBalance = 1_000_000
const initTraderTokenBalance = 1_000
const initAccountBalanceTinyBars = 10_000 * 100_000_000

func SetupSimFromParams(client *hedera.Client) {
	simParams := LoadParams()

	fmt.Printf(`Using simulation params,
  ERC20 tokens : %s
  # traders    : %d
  # LPs        : %d%s`,
		strings.Join(simParams.TokenNames, ", "),
		simParams.NumTraders,
		simParams.NumLiquidityProviders,
		"\n\n")

	factoryInitcodeId := UploadInitcode(client, "./assets/bytecode/UniswapV3Factory.bin")
	fmt.Printf("Factory initcode is at file %s\n", factoryInitcodeId.String())
	factoryId := createContractVia(client, factoryInitcodeId, &hedera.ContractFunctionParameters{})
	fmt.Printf("🏭 Factory contract deployed to %s\n", factoryId.String())

	erc20InitcodeId := UploadInitcode(client, "./assets/bytecode/NamedERC20.bin")
	fmt.Printf("💰 ERC20 initcode is at file %s\n", erc20InitcodeId.String())
	var tickers []string
	var tokenIds []string
	var typedTokenIds []hedera.ContractID
	for _, name := range simParams.TokenNames {
		symbol := symbolFor(name)
		var consParams = hedera.NewContractFunctionParameters().
			AddString(name).
			AddString(symbol).
			AddInt32(initTokenSupply)
		erc20Id := createContractVia(client, erc20InitcodeId, consParams)
		tickers = append(tickers, symbol)
		tokenIds = append(tokenIds, erc20Id.ToSolidityAddress())
		typedTokenIds = append(typedTokenIds, erc20Id)
		fmt.Printf("  -> Token '%s' (%s) deployed to %s\n", name, symbol, erc20Id.String())
	}

	fmt.Println("\nNow creating pairs...")
	for i, a := range typedTokenIds {
		for j, b := range typedTokenIds[(i + 1):] {
			k := i + j + 1
			var deployParams = hedera.NewContractFunctionParameters()
			_, err := deployParams.AddAddress(a.ToSolidityAddress())
			if err != nil {
				panic(err)
			}
			_, err = deployParams.AddAddress(b.ToSolidityAddress())
			if err != nil {
				panic(err)
			}
			deployParams.AddUint32(poolFee)
			record := CallContractVia(client, factoryId, "createPool", deployParams)
			result, err := record.Children[0].GetContractCreateResult()
			if err != nil {
				panic(err)
			}
			fmt.Printf("  ☑️  Created %s/%s pair @ %s (Hedera id %s)...",
				tickers[i],
				tickers[k],
				result.EvmAddress.String(),
				record.Children[0].Receipt.ContractID.String())

			var initParams = hedera.NewContractFunctionParameters().AddUint64(initSqrtPriceX96)
			initRecord := CallContractVia(client, result.EvmAddress, "initialize", initParams)
			fmt.Printf("initialization at 1:1 price returned %s\n", initRecord.Receipt.Status)
		}
	}

	fmt.Println("\nNow creating participants...")
	lpInitcodeId := UploadInitcode(client, "./assets/bytecode/MinimalV3LP.bin")
	fmt.Printf("LP initcode is at file %s\n", lpInitcodeId.String())
	var traderIds []string
	for i := simParams.NumTraders; i > 0; i-- {
		nextId := createSuppliedAccountVia(client, initTraderTokenBalance, typedTokenIds)
		traderIds = append(traderIds, nextId.String())
		fmt.Printf("😨 Trader #%d created at %s, all ticker balances initialized to %d\n",
			simParams.NumTraders-i+1, nextId.String(), initTraderTokenBalance)
	}

	var lpIds []string
	for i := simParams.NumLiquidityProviders; i > 0; i-- {
		var consParams = hedera.NewContractFunctionParameters()
		_, err := consParams.AddAddress(factoryId.ToSolidityAddress())
		if err != nil {
			panic(err)
		}
		nextId := createContractVia(client, lpInitcodeId, consParams)
		lpIds = append(lpIds, nextId.ToSolidityAddress())
		fundAccountVia(client, initLpTokenBalance, typedTokenIds, nextId)
		fmt.Printf("🤑 Liquidity provider #%d created at %s, all ticker balances initialized to %d\n",
			simParams.NumLiquidityProviders-i+1, nextId.String(), initLpTokenBalance)
	}

	simDetails := details{
		LpIds:     lpIds,
		Tickers:   tickers,
		TokenIds:  tokenIds,
		TraderIds: traderIds,
		FactoryId: factoryId.String(),
	}

	rawSimDetails, err := json.Marshal(simDetails)
	if err != nil {
		panic(err)
	}
	err = ioutil.WriteFile("./assets/details.json", rawSimDetails, 0644)
	if err != nil {
		panic(err)
	}
}

func createSuppliedAccountVia(
	client *hedera.Client,
	initBalance uint32,
	tokenIds []hedera.ContractID,
) hedera.AccountID {
	txnId, err := hedera.NewAccountCreateTransaction().
		SetInitialBalance(hedera.HbarFromTinybar(initAccountBalanceTinyBars)).
		SetKey(client.GetOperatorPublicKey()).
		Execute(client)
	if err != nil {
		panic(err)
	}

	record, err := txnId.GetRecord(client)
	if err != nil {
		panic(err)
	}

	accountId := *record.Receipt.AccountID
	for _, tokenId := range tokenIds {
		var transferParams, err = hedera.NewContractFunctionParameters().
			AddAddress(accountId.ToSolidityAddress())
		if err != nil {
			panic(err)
		}
		transferParams.AddUint256(asUint256(initBalance))
		CallContractVia(client, tokenId, "transfer", transferParams)
	}
	return accountId
}

func fundAccountVia(
	client *hedera.Client,
	initBalance uint32,
	tokenIds []hedera.ContractID,
	recipient hedera.ContractID,
) {
	for _, tokenId := range tokenIds {
		var transferParams, err = hedera.NewContractFunctionParameters().
			AddAddress(recipient.ToSolidityAddress())
		if err != nil {
			panic(err)
		}
		transferParams.AddUint256(asUint256(initBalance))
		CallContractVia(client, tokenId, "transfer", transferParams)
	}
}

func asUint256(v uint32) []byte {
	ans := make([]byte, 32)
	binary.BigEndian.PutUint32(ans[28:32], v)
	return ans
}

func createContractVia(
	client *hedera.Client,
	initcode hedera.FileID,
	params *hedera.ContractFunctionParameters,
) hedera.ContractID {
	txnId, err := hedera.NewContractCreateTransaction().
		SetGas(4_000_000).
		SetConstructorParameters(params).
		SetBytecodeFileID(initcode).
		Execute(client)
	if err != nil {
		panic(err)
	}

	record, err := txnId.GetRecord(client)
	if err != nil {
		panic(err)
	}

	return *record.Receipt.ContractID
}

func symbolFor(erc20Token string) string {
	words := strings.Split(erc20Token, " ")
	var symbol = ""
	for _, word := range words {
		symbol += word[0:1]
	}
	return symbol
}
