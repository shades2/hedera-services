package misc

import (
	"fmt"
	"github.com/hashgraph/hedera-sdk-go/v2"
	"math/rand"
	"time"
)

const swapAmount uint64 = 1_000_000_000

func DoSwaps(client *hedera.Client) {
	simParams := LoadParams()
	simDetails := LoadDetails()

	i := 0
	for {
		doFixedInput := rand.Intn(2) == 0
		SwapRandomlyGiven(client, simDetails, simParams, doFixedInput)

		fmt.Printf("\n💤 Now sleeping %dms...\n\n", simParams.MillisBetweenSwaps)
		time.Sleep(time.Duration(simParams.MillisBetweenSwaps) * time.Millisecond)

		displayBalances := i % simParams.SwapsBetweenBalanceDisplay == 0

		if displayBalances {
			fmt.Println("=============== CURRENT BALANCES ===============")
			for _, trader:= range simDetails.TraderIds {
				traderId, err := hedera.ContractIDFromSolidityAddress(trader)
				if err != nil {
					panic(err)
				}
				fmt.Printf("💰 Trader %s balances are: \n", traderId)
				for j, token:= range simDetails.TokenIds {
					tokenId, err := hedera.ContractIDFromSolidityAddress(token)
					if err != nil {
						panic(err)
					}
					ticker := "$" + simDetails.Tickers[j]
					balance := BalanceVia(client, tokenId, trader)
					fmt.Printf("  %s: %d\n", ticker, balance)
				}
			}
			fmt.Println("================================================")
		}
		i++
	}
}

func SwapRandomlyGiven(
	client *hedera.Client,
	simDetails details,
	simParams params,
	fixedInput bool,
) {
	numTickers := len(simDetails.Tickers)
	numTraders := len(simDetails.TraderIds)

	chosenTrader := rand.Intn(numTraders)
	traderId, err := hedera.ContractIDFromSolidityAddress(simDetails.TraderIds[chosenTrader])
	if err != nil {
		panic(err)
	}

	inChoice := rand.Intn(numTickers)
	outChoice := inChoice
	for outChoice == inChoice {
		outChoice = rand.Intn(numTickers)
	}
	tickerIn := "$" + simDetails.Tickers[inChoice]
	tickerOut := "$" + simDetails.Tickers[outChoice]

	tokenIn, err := hedera.ContractIDFromSolidityAddress(simDetails.TokenIds[inChoice])
	if err != nil {
		panic(err)
	}
	tokenOut, err := hedera.ContractIDFromSolidityAddress(simDetails.TokenIds[outChoice])
	if err != nil {
		panic(err)
	}

	if fixedInput {
		fmt.Printf("🍄 Trader %s Looking to swap %d %s for as much %s as possible\n",
			traderId, swapAmount, tickerIn, tickerOut)
		swapExactInput(client, tokenIn, tokenOut, swapAmount, traderId, simParams.CheckSwapRecords)
	} else {
		fmt.Printf("🌳 Trader %s Looking to swap up %d %s for %d %s\n",
			traderId, swapAmount, tickerIn, swapAmount/2, tickerOut)
		swapExactOutput(client, tokenIn, tokenOut, swapAmount, swapAmount/2, traderId, simParams.CheckSwapRecords)
	}
}

func swapExactInput(
	client *hedera.Client,
	tokenIn hedera.ContractID,
	tokenOut hedera.ContractID,
	amountIn uint64,
	traderId hedera.ContractID,
	getRecord bool,
) {
	encAmountIn := Uint256From64(amountIn)
	var swapParams = hedera.NewContractFunctionParameters()
	_, err := swapParams.AddAddress(tokenIn.ToSolidityAddress())
	if err != nil {
		panic(err)
	}
	_, err = swapParams.AddAddress(tokenOut.ToSolidityAddress())
	if err != nil {
		panic(err)
	}
	fmt.Print("  🐲 Swapping...")
	swapParams.
		AddUint256(encAmountIn)
	if getRecord {
		swapRecord := CallContractTentatively(client, traderId, "swapExactInputSingle", swapParams)
		status := swapRecord.Receipt.Status
		if status == hedera.StatusSuccess {
			amountOut := swapRecord.CallResult.GetUint64(0)
			fmt.Printf("got %d units back\n", amountOut)
		} else {
			fmt.Printf("%s\n", status)
		}
	} else {
		FireAndForget(client, traderId, "swapExactInputSingle", swapParams)
		fmt.Print("OK\n")
	}
}

func swapExactOutput(
	client *hedera.Client,
	tokenIn hedera.ContractID,
	tokenOut hedera.ContractID,
	maxAmountIn uint64,
	amountOut uint64,
	traderId hedera.ContractID,
	getRecord bool,
) {
	encMaxAmountIn := Uint256From64(maxAmountIn)
	encAmountOut := Uint256From64(amountOut)
	var swapParams = hedera.NewContractFunctionParameters()
	_, err := swapParams.AddAddress(tokenIn.ToSolidityAddress())
	if err != nil {
		panic(err)
	}
	_, err = swapParams.AddAddress(tokenOut.ToSolidityAddress())
	if err != nil {
		panic(err)
	}
	swapParams.
		AddUint256(encAmountOut).
		AddUint256(encMaxAmountIn)
	fmt.Print("  🐲 Swapping...")
	if getRecord {
		swapRecord := CallContractTentatively(client, traderId, "swapExactOutputSingle", swapParams)
		status := swapRecord.Receipt.Status
		if status == hedera.StatusSuccess {
			amountIn := swapRecord.CallResult.GetUint64(0)
			fmt.Printf("required %d units\n", amountIn)
		} else {
			fmt.Printf("%s\n", status)
		}
	} else {
		FireAndForget(client, traderId, "swapExactOutputSingle", swapParams)
		fmt.Print("OK\n")
	}
}
