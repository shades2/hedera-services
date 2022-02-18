package misc

import (
	"fmt"
	"github.com/hashgraph/hedera-sdk-go/v2"
	"math/rand"
	"time"
)

var mintFracDenom = []int{10, 100, 1000}

func DoMints(client *hedera.Client) {
	simParams := LoadParams()
	simDetails := LoadDetails()

	numTickers := len(simDetails.Tickers)
	//numLps := len(simDetails.LpIds)
	liquidityId, err := hedera.ContractIDFromSolidityAddress(simDetails.LiquidityId)

	if err != nil {
		panic(err)
	}
	for {
		// TODO - choose our liquidity provider
		//choice := rand.Intn(numLps)
		//lpId, err := hedera.ContractIDFromSolidityAddress(simDetails.LpIds[choice])
		//if err != nil {
		//	panic(err)
		//}

		// Choose our tickers to mint
		choice := rand.Intn(numTickers)
		secondChoice := choice
		for secondChoice == choice {
			secondChoice = rand.Intn(numTickers)
		}
		tickerA := simDetails.Tickers[choice]
		tickerB := simDetails.Tickers[secondChoice]

		tokenA, err := hedera.ContractIDFromSolidityAddress(simDetails.TokenIds[choice])
		if err != nil {
			panic(err)
		}
		tokenB, err := hedera.ContractIDFromSolidityAddress(simDetails.TokenIds[secondChoice])
		if err != nil {
			panic(err)
		}

		lpId := client.GetOperatorAccountID()
		aPriorBalance := BalanceVia(client, tokenA, lpId.ToSolidityAddress())
		bPriorBalance := BalanceVia(client, tokenB, lpId.ToSolidityAddress())
		fmt.Printf("🍵 LP %s looking to mint %s/%s\n", lpId, tickerA, tickerB)
		fmt.Printf("  💰 $%s: %d\n", tickerA, aPriorBalance)
		fmt.Printf("  💰 $%s: %d\n", tickerB, bPriorBalance)
		mintVia(client, tokenA, tokenB, 1000, 1000, liquidityId)

		aPostBalance := BalanceVia(client, tokenA, lpId.ToSolidityAddress())
		bPostBalance := BalanceVia(client, tokenB, lpId.ToSolidityAddress())
		fmt.Printf("  -->> $%s: %d\n", tickerA, aPostBalance)
		fmt.Printf("  -->> $%s: %d\n", tickerB, bPostBalance)

		fmt.Printf("💤 Sleeping %d seconds...\n\n", simParams.SecsBetweenMints)
		time.Sleep(time.Duration(simParams.SecsBetweenMints) * time.Second)
	}
}

func mintVia(
	client *hedera.Client,
	token0 hedera.ContractID,
	token1 hedera.ContractID,
	amount0 uint64,
	amount1 uint64,
	liquidityId hedera.ContractID,
) {
	encAmount0 := Uint256From64(amount0)
	encAmount1 := Uint256From64(amount1)
	var mintParams = hedera.NewContractFunctionParameters()
	_, err := mintParams.AddAddress(token0.ToSolidityAddress())
	if err != nil {
		panic(err)
	}
	_, err = mintParams.AddAddress(token1.ToSolidityAddress())
	if err != nil {
		panic(err)
	}
	fmt.Print("💸 Minting new liquidity...")
	mintParams.
		AddUint256(encAmount0).
		AddUint256(encAmount1)
	mintRecord := CallContractVia(client, liquidityId, "mintNewPosition", mintParams)
	fmt.Println(mintRecord.CallResult.ErrorMessage)
	fmt.Printf("%s\n", mintRecord.Receipt.Status)
}
