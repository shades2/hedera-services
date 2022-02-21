package misc

import (
	"fmt"
	"github.com/hashgraph/hedera-sdk-go/v2"
	"math/rand"
	"time"
)

const mintAmount uint64 = 1_000_000_000_000

func DoMints(client *hedera.Client) {
	simParams := LoadParams()
	simDetails := LoadDetails()

	for {
		MintRandomlyGiven(client, simDetails)

		fmt.Printf("💤 Sleeping %dms...\n\n", simParams.MillisBetweenMints)
		time.Sleep(time.Duration(simParams.MillisBetweenMints) * time.Millisecond)
	}
}

func MintRandomlyGiven(
	client *hedera.Client,
	simDetails details,
) {
	numTickers := len(simDetails.Tickers)
	numLps := len(simDetails.LpIds)
	chosenLp := rand.Intn(numLps)
	lpId, err := hedera.ContractIDFromSolidityAddress(simDetails.LpIds[chosenLp])
	if err != nil {
		panic(err)
	}

	// Choose our tickers to mint
	firstChoice := rand.Intn(numTickers)
	secondChoice := firstChoice
	for secondChoice == firstChoice {
		secondChoice = rand.Intn(numTickers)
	}
	tickerA := "$" + simDetails.Tickers[firstChoice]
	tickerB := "$" + simDetails.Tickers[secondChoice]

	tokenA, err := hedera.ContractIDFromSolidityAddress(simDetails.TokenIds[firstChoice])
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
	fmt.Printf("  💰 %s: %d\n", tickerA, aPriorBalance)
	fmt.Printf("  💰 %s: %d\n", tickerB, bPriorBalance)
	mintVia(client, tokenA, tokenB, mintAmount, mintAmount, lpId)

	aPostBalance := BalanceVia(client, tokenA, lpId.ToSolidityAddress())
	bPostBalance := BalanceVia(client, tokenB, lpId.ToSolidityAddress())
	fmt.Printf("  -->> $%s: %d\n", tickerA, aPostBalance)
	fmt.Printf("  -->> $%s: %d\n", tickerB, bPostBalance)
}

func mintVia(
	client *hedera.Client,
	token0 hedera.ContractID,
	token1 hedera.ContractID,
	amount0 uint64,
	amount1 uint64,
	lpId hedera.ContractID,
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
	fmt.Print("💸 Minting new liquidity position...")
	mintParams.
		AddUint256(encAmount0).
		AddUint256(encAmount1)
	mintRecord := CallContractVia(client, lpId, "mintNewPosition", mintParams)
	fmt.Printf("%s\n", mintRecord.Receipt.Status)
}
