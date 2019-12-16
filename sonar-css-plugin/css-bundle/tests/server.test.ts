import { start } from "../src/server";
import * as http from "http";
import { Server } from "http";
import { promisify } from "util";
import { AddressInfo } from "net";
import * as stylelint from "stylelint";

const request = JSON.stringify({
  filePath: __dirname + "/fixtures/file.css",
  configFile: __dirname + "/fixtures/stylelintconfig.json"
});
jest.mock("stylelint");

describe("server", () => {
  let server: Server;
  let close: () => Promise<void>;

  beforeEach(async () => {
    server = await start();
    close = promisify(server.close.bind(server));
    console.log = jest.fn();
    console.error = jest.fn();
  });

  afterEach(async () => {
    jest.resetAllMocks();
    await close();
  });

  it("should respond to analysis request", async () => {
    expect(server.listening).toEqual(true);
    const response = await post(request, "/analyze");
    expect(JSON.parse(response)).toEqual([
      {
        line: 1,
        rule: "block-no-empty",
        text: "Unexpected empty block (block-no-empty)"
      }
    ]);
    expect(console.log).toBeCalledTimes(0);
    expect(console.error).toBeCalledTimes(0);
  });

  it("should cut BOM", async () => {
    expect(server.listening).toEqual(true);
    const response = await post(
      JSON.stringify({
        filePath: __dirname + "/fixtures/file-bom.css",
        configFile: __dirname + "/fixtures/stylelintconfig.json"
      }),
      "/analyze"
    );
    expect(JSON.parse(response)).toEqual([
      {
        line: 1,
        rule: "block-no-empty",
        text: "Unexpected empty block (block-no-empty)"
      }
    ]);
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

  it("should return empty list of issues when invalid json", async () => {
    const response = await post("invalid json", "/analyze");
    expect(JSON.parse(response)).toEqual([]);
    expect(console.log).toHaveBeenCalledWith(
      expect.stringContaining(
        "SyntaxError: Unexpected token i in JSON at position 0"
      )
    );
  });

  it("should return empty list of issues when invalid request", async () => {
    const response = await post("{}", "/analyze");
    expect(JSON.parse(response)).toEqual([]);
    expect(console.log).toHaveBeenCalledWith(
      expect.stringContaining("TypeError: path must be a string or Buffer")
    );
  });

  it("should not return issues for not original file", async () => {
    (stylelint.lint as any).mockResolvedValue(
      Promise.resolve({ results: [{ source: "foo.bar" }] })
    );
    const response = await post(request, "/analyze");
    expect(JSON.parse(response)).toEqual([]);
    expect(console.log).toHaveBeenCalledWith(
      expect.stringContaining("DEBUG For file [")
    );
    expect(console.log).toHaveBeenCalledWith(
      expect.stringContaining(
        "sonar-css-plugin/css-bundle/tests/fixtures/file.css] received issues with [foo.bar] as a source."
      )
    );
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
