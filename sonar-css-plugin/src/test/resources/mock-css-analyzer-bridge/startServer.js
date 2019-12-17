#!/usr/bin/env node

const http = require('http');
const port = process.argv[2];

const requestHandler = (request, response) => {
  let data = [];
  request.on('data', chunk => {
    data.push(chunk);
  });
  request.on('end', () => {
    let fileName = null;
    if (data.length > 0) {
      const analysisRequest = JSON.parse(data.join());
      fileName = analysisRequest.filePath.replace(/.*[\/\\]/g,"");
    }
    if (request.url === '/status') {
      response.writeHead(200, { 'Content-Type': 'text/plain' });
      response.end('OK!');
    } else {
      switch (fileName) {
        case "foo.css":
          response.end(JSON.stringify([
            {line: 42, rule: "block-no-empty", text: "Unexpected empty block"}
          ]));
          break;
        case "file.css":
          response.end(JSON.stringify([
            {line: 2, rule: "color-no-invalid-hex", text: "some message (color-no-invalid-hex)"}
          ]));
          break;
        case "message-without-rule-id.css":
          response.end(JSON.stringify([
            {line: 2, rule: "color-no-invalid-hex", text: "some message"}
          ]));
          break;
        case "empty.css":
          response.end(JSON.stringify([]));
          break;
        case "syntax-error.css":
          response.end(JSON.stringify([
            {line: 2, rule: "CssSyntaxError", text: "Missed semicolon (CssSyntaxError)"}
          ]));
          break;
        case "unknown-rule.css":
          response.end(JSON.stringify([
            {line: 2, rule: "unknown-rule-key", text: "some message"}
          ]));
          break;
        case "invalid-json-response.css":
          response.end("[");
          break;
        default:
          throw "Unexpected fileName: " + fileName;
      }
    }
  });
};

const server = http.createServer(requestHandler);

server.listen(port, (err) => {
  if (err) {
    return console.log('something bad happened', err)
  }

  console.log(`server is listening on ${port}`)
});
