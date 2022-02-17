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
	numLps := len(simDetails.LpIds)
	for {
		// Choose our liquidity provider
		choice := rand.Intn(numLps)
		lpId, err := hedera.ContractIDFromSolidityAddress(simDetails.LpIds[choice])
		if err != nil {
			panic(err)
		}

		// Choose our tickers to mint
		choice = rand.Intn(numTickers)
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

		aPriorBalance := BalanceVia(client, tokenA, lpId.ToSolidityAddress())
		bPriorBalance := BalanceVia(client, tokenB, lpId.ToSolidityAddress())
		fmt.Printf("🍵 LP %s looking to mint %s/%s\n", lpId, tickerA, tickerB)
		fmt.Printf("  💰 $%s: %d\n", tickerA, aPriorBalance)
		fmt.Printf("  💰 $%s: %d\n", tickerB, bPriorBalance)

		var mintParams = hedera.NewContractFunctionParameters()
		_, err = mintParams.AddAddress(tokenA.ToSolidityAddress())
		if err != nil {
			panic(err)
		}
		_, err = mintParams.AddAddress(tokenB.ToSolidityAddress())
		if err != nil {
			panic(err)
		}
		var amount uint64 = 1_000_000
		fmt.Print("💸 Minting new liquidity...")
		mintParams.
			AddUint64(amount).
			AddUint32(poolFee)
		mintRecord := CallContractVia(client, lpId, "mint", mintParams)
		fmt.Printf("%s\n", mintRecord.Receipt.Status)

		aPostBalance := BalanceVia(client, tokenA, lpId.ToSolidityAddress())
		bPostBalance := BalanceVia(client, tokenB, lpId.ToSolidityAddress())
		fmt.Printf("  -->> $%s: %d\n", tickerA, aPostBalance)
		fmt.Printf("  -->> $%s: %d\n", tickerB, bPostBalance)

		fmt.Printf("💤 Sleeping %d seconds...\n\n", simParams.SecsBetweenMints)
		time.Sleep(time.Duration(simParams.SecsBetweenMints) * time.Second)
	}
}
