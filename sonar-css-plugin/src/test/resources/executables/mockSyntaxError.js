#!/usr/bin/env node
var testFile = process.argv[2];

var result = [
  {
    source: testFile,

    warnings: [
      {
        text: "Missed semicolon (CssSyntaxError)",
        line: 2,
        rule: "CssSyntaxError"
      }
    ]
  }
];

var json = JSON.stringify(result);
console.log(json);
