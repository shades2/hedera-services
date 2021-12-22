// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.9;
import "./hip-206/HederaTokenService.sol";
import "./hip-206/HederaResponseCodes.sol";

contract TransferAmountAndToken is HederaTokenService {

    address tokenAddress;

    constructor(address _tokenAddress) public {
        tokenAddress = _tokenAddress;
    }

    function transferNFTToAddress(address _address, address _address2, int64 serialNum, int64 serialNum2) public {

        int response = HederaTokenService.transferNFT(tokenAddress, _address, _address2, serialNum);

        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Non fungible token transfer failed");
        }

        int response2 = HederaTokenService.transferNFT(tokenAddress, _address, _address2, serialNum2);

        if (response2 != HederaResponseCodes.SUCCESS) {
            revert ("Non fungible token transfer2 failed");
        }

    }

    function transferFungibleTokenToAddress(address _address, address _address2, int64 amount) public {
        int response = HederaTokenService.transferToken(tokenAddress, _address, _address2, amount);

        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Fungible token transfer failed");
        }
    }

}