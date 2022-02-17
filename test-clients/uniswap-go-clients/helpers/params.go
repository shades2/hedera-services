package misc

import (
	"encoding/json"
	"io/ioutil"
)

type params struct {
	SecsBetweenSwaps      int      `json:"secsBetweenSwaps"`
	SecsBetweenMints      int      `json:"secsBetweenMints"`
	TokenNames            []string `json:"erc20TokenNames"`
	NumTraders            int      `json:"numTraders"`
	NumLiquidityProviders int      `json:"numLiquidityProviders"`
}

func LoadParams() params {
	rawParams, err := ioutil.ReadFile("./assets/params.json")
	if err != nil {
		panic(err)
	}
	simParams := params{}
	err = json.Unmarshal(rawParams, &simParams)
	if err != nil {
		panic(err)
	}
	return simParams
}
