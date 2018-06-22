#!/usr/bin/env node
var testFile = process.argv[2];

var result = [
  {
    source: testFile,

    warnings: [
      {
        text: "some message (color-no-invalid-hex)",
        line: 2,
        rule: "color-no-invalid-hex"
      }
    ]
  }
];

var json = JSON.stringify(result);
console.log(json);
