package misc

import (
	"encoding/json"
	"fmt"
	"github.com/hashgraph/hedera-sdk-go/v2"
	"io/ioutil"
	"strings"
)

type params struct {
	TokenNames            []string `json:"erc20TokenNames"`
	NumTraders            int32    `json:"numTraders"`
	NumLiquidityProviders int32    `json:"numLiquidityProviders"`
}

func SetupSimFromParams(client *hedera.Client) {
	rawParams, err := ioutil.ReadFile("./assets/params.json")
	if err != nil {
		panic(err)
	}
	simParams := params{}
	err = json.Unmarshal(rawParams, &simParams)
	if err != nil {
		panic(err)
	}

	fmt.Printf(`--- SIMULATION PARAMS ---
  ERC20 tokens : %s
  # traders    : %d
  # LPs        : %d
-------------------------%s`,
		strings.Join(simParams.TokenNames, ", "),
		simParams.NumTraders,
		simParams.NumLiquidityProviders,
		"\n\n")

	fileId := UploadInitcode(client, "./assets/bytecode/UniswapV3Factory.bin")

	fmt.Printf("- Factory initcode is at file %s\n", fileId.String())
}
