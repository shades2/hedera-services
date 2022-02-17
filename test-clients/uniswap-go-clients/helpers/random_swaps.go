package misc

import (
	"fmt"
	"github.com/hashgraph/hedera-sdk-go/v2"
	"math/rand"
	"time"
)

func DoSwaps(client *hedera.Client) {
	simParams := LoadParams()
	simDetails := LoadDetails()

	numTickers := len(simDetails.Tickers)
	numTraders := len(simDetails.TraderIds)
	for {
		// Choose our trader
		choice := rand.Intn(numTraders)
		traderId, err := hedera.AccountIDFromString(simDetails.TraderIds[choice])
		if err != nil {
			panic(err)
		}

		// Choose our tickers to swap
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

		fmt.Println(tokenA.String())
		fmt.Println(tokenB.String())
		aBalance := BalanceVia(client, tokenA, traderId.ToSolidityAddress())
		bBalance := BalanceVia(client, tokenB, traderId.ToSolidityAddress())

		fmt.Printf("🍄 Trader %s Looking to swap %s for %s\n", traderId, tickerA, tickerB)
		fmt.Printf("  -> %s balance : %d\n", tickerA, aBalance)
		fmt.Printf("  -> %s balance : %d\n", tickerB, bBalance)

		fmt.Printf("💤 Now sleeping %d seconds...\n\n", simParams.SecsBetweenSwaps)
		time.Sleep(time.Duration(simParams.SecsBetweenSwaps) * time.Second)
	}
}
