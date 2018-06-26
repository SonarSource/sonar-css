#!/usr/bin/env node
var testFile = process.argv[2];

var result = [
  {
    source: testFile,

    warnings: [
      {
        text: "some message",
        line: 2,
        rule: "unknown-rule-key"
      }
    ]
  }
];

var json = JSON.stringify(result);
console.log(json);
