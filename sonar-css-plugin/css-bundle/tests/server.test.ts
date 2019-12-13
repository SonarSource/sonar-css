import { start } from "../src/server";
import * as http from "http";
import { Server } from "http";
import { promisify } from "util";
import { AddressInfo } from "net";

const request = JSON.stringify({
  filePath: __dirname + "/fixtures/file.css",
  configFile: __dirname + "/fixtures/stylelintconfig.json"
});

describe("server", () => {
  let server: Server;
  let close: () => Promise<void>;

  beforeEach(async () => {
    server = await start();
    close = promisify(server.close.bind(server));
  });

  afterEach(async () => {
    await close();
  });

  it("should respond to analysis request", async () => {
    expect(server.listening).toEqual(true);

    const response = await post(request, "/analyze");
    console.log(response);
    expect(JSON.parse(response)).toEqual([{
      line: 1,
      rule: "block-no-empty",
      text: "Unexpected empty block (block-no-empty)"
    }]);
  });

  it("should respond OK! when started", done => {
    expect(server.listening).toEqual(true);
    const req = http.request(
      {
        host: "localhost",
        port: (<AddressInfo>server.address()).port,
        path: "/status",
        method: "GET"
      },
      res => {
        let data = "";
        res.on("data", chunk => {
          data += chunk;
        });
        res.on("end", () => {
          expect(data).toEqual("OK!");
          done();
        });
      }
    );
    req.end();
  });

  it.only("should not fail when invalid request", async () => {
    const response = await post(
      "invalid request",
      "/analyze"
    );
    console.log(response);
    expect(JSON.parse(response)).toEqual([]);
  });

  function post(data: string, endpoint: string): Promise<string> {
    const options = {
      host: "localhost",
      port: (<AddressInfo>server.address()).port,
      path: endpoint,
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      }
    };

    return new Promise((resolve, reject) => {
      let response = "";

      const req = http.request(options, res => {
        res.on("data", chunk => {
          response += chunk;
        });

        res.on("end", () => resolve(response));
      });

      req.on("error", reject);

      req.write(data);
      req.end();
    });
  }
});
