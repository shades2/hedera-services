// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;
import "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import "@openzeppelin/contracts/token/ERC20/extensions/IERC20Metadata.sol";

contract ERC20Contract {

    function name(address token) public {
        IERC20Metadata(token).name();
    }

    function symbol(address token) public {
        IERC20Metadata(token).symbol();
    }

    function decimals(address token) public {
        IERC20Metadata(token).decimals();
    }

    function totalSupply(address token) public {
        IERC20(token).totalSupply();
    }

    function balanceOf(address token, address account) public {
        IERC20(token).balanceOf(account);
    }

    function transfer(address token, address recipient, uint256 amount) public {
        IERC20(token).transfer(recipient, amount);
    }

    function transferFrom(address token, address sender, address recipient, uint256 amount) public {
        IERC20(token).transferFrom(sender, recipient, amount);
    }
}