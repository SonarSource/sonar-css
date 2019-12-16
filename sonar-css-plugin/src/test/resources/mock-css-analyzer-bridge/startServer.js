#!/usr/bin/env node

const http = require('http');
const port = process.argv[2];

const requestHandler = (request, response) => {
  let data = [];
  request.on('data', chunk => {
    data.push(chunk);
  });
  request.on('end', () => {
    let filePath = null;
    if (data.length > 0) {
      const analysisRequest = JSON.parse(data.join());
      filePath = analysisRequest.filePath;
    }
    if (request.url === '/status') {
      response.writeHead(200, { 'Content-Type': 'text/plain' });
      response.end('OK!');
    } else if (filePath !== null && filePath.endsWith("foo.css")) {
      response.end("[ {'line': 42, rule: 'block-no-empty', 'text': 'Unexpected empty block'} ]");
    } else if (filePath !== null && filePath.endsWith("file.css")) {
      // response.end("[ {'line': 2, rule: 'color-no-invalid-hex', 'text': 'some message (color-no-invalid-hex)'} ]");
      response.end("[]");
    } else if (filePath !== null && filePath.endsWith("empty.css")) {
      response.end("[]");
    } else {
      throw "Unexpected filePath: " + filePath;
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
