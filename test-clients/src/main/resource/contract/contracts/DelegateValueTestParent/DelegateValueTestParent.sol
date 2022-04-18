// SPDX-License-Identifier: GPL-3.0

pragma solidity >=0.7.0 <0.9.0;

interface Helper {
    function callMe() external;
}

contract DelegateValueTestParent {

    event AnEvent(uint val);

    DelegateValueTest test1;
    DelegateValueTest test2;

    constructor() payable {

        test1 = new DelegateValueTest();
        test2 = new DelegateValueTest();

        uint value = test1.makeDelegateCall{value:msg.value/2}(address(test2));

        emit AnEvent(value);
    }

}

contract DelegateValueTest {

    function makeDelegateCall(address _address) payable public returns (uint value) {
        (bool success, bytes memory result) = _address.delegatecall(abi.encodeWithSelector(Helper.callMe.selector));
        if (success) {
            value = abi.decode(result, (uint));
        }
    }

    function callMe() payable external returns (uint value) {
        value = msg.value;
    }

}
