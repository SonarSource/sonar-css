#!/usr/bin/env node

const http = require('http')
const port = process.argv[2]

const requestHandler = (request, response) => {
  if (request.url == '/status') {
    response.writeHead(200, { 'Content-Type': 'text/plain' });
    response.end('OK!');
  } else {
    response.end("Invalid response");
  }
}

const server = http.createServer(requestHandler)

server.listen(port, (err) => {
  if (err) {
    return console.log('something bad happened', err)
  }

  console.log(`server is listening on ${port}`)
})
