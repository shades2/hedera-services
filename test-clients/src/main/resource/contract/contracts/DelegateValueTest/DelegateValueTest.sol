// SPDX-License-Identifier: GPL-3.0

pragma solidity >=0.7.0 <0.9.0;



interface Helper {

    function callMe() external;
}

contract DelegateValueTest {

    event AnEvent(uint val);

    function makeDelegateCall(address _address) payable public {
        _address.delegatecall(abi.encodeWithSelector(Helper.callMe.selector));
    }

    function callMe() payable external {
        emit AnEvent(msg.value);
    }

}