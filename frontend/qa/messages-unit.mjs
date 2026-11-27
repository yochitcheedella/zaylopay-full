import test from 'node:test';
import assert from 'node:assert/strict';
import {messages, customerError, safeErrorData} from '../src/utils/messages.js';
test('provider wording is exact',()=>assert.equal(messages.provider,'Please use Gmail, Yahoo, Outlook or another supported email address.'));
test('untrusted exception text never reaches customers',()=>{
 for(const data of [{detail:'SQL password stacktrace',message:'secret'},'database failed',null]) {
  const safe=safeErrorData(data,500); assert.equal(safe.detail,'We cannot complete this right now. Please try again shortly.');
 }
});
test('field errors use safe local copy while keeping field names',()=>{
 const safe=safeErrorData({code:'VALIDATION_FAILED',errors:[{field:'email',message:'SQL exception'}]},400);
 assert.equal(safe.errors[0].message,messages.email); assert.equal(safe.errors[0].field,'email');
});
test('OTP and payment errors give recovery instructions',()=>{
 assert.match(customerError({code:'OTP_EXPIRED'},400),/request a new code/);
 assert.match(customerError({code:'PAYMENT_GATEWAY_ERROR'},502),/check your payment status/);
});
test('rate limit metadata survives safe mapping',()=>{
 const safe=safeErrorData({code:'OTP_RATE_LIMITED',retryAfterSeconds:60,rateLimitScope:'IP'},429);
 assert.equal(safe.retryAfterSeconds,60); assert.equal(safe.rateLimitScope,'IP');
 assert.doesNotMatch(safe.detail,/account.*found/);
});
