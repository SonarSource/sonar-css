import { start, setLogHandlersForTests } from "../src/server";
import * as http from "http";
import { Server } from "http";
import { promisify } from "util";
import { AddressInfo } from "net";
import { postToServer } from "./utils";
import * as path from "path";

const configFile = path.join(__dirname, "fixtures", "stylelintconfig.json");

describe("server", () => {
  let server: Server;
  let close: () => Promise<void>;
  const logSpy = jest.fn();
  const errorSpy = jest.fn();

  beforeAll(async () => {
    setLogHandlersForTests(logSpy, errorSpy);
    server = await start();
    close = promisify(server.close.bind(server));
  });

  afterAll(async () => {
    jest.restoreAllMocks();
    await close();
  });

  it("should log with debug server start", async () => {
    expect(server.listening).toEqual(true);
    expect(logSpy).toBeCalledTimes(2);
    expect(logSpy).toBeCalledWith(
      "DEBUG starting stylelint-bridge server at port",
      0
    );
    expect(logSpy).toBeCalledWith(
      "DEBUG stylelint-bridge server is running at port",
      (<AddressInfo>server.address()).port
    );
    expect(errorSpy).toBeCalledTimes(0);
  });

  it("should respond to analysis request", async () => {
    const request = JSON.stringify({
      filePath: path.join(__dirname, "fixtures", "file.css"),
      configFile
    });
    const response = await post(request, "/analyze");
    expect(JSON.parse(response)).toEqual([
      {
        line: 1,
        rule: "block-no-empty",
        text: "Unexpected empty block (block-no-empty)"
      }
    ]);
  });

  it("should respond to analysis request for php", async () => {
    const requestPhp = JSON.stringify({
      filePath: path.join(__dirname, "fixtures", "file.php"),
      configFile
    });
    const responsePhp = await post(requestPhp, "/analyze");
    expect(JSON.parse(responsePhp)).toEqual([
      {
        line: 7,
        rule: "block-no-empty",
        text: "Unexpected empty block (block-no-empty)"
      }
    ]);
  });

  it("should respond to analysis request for html", async () => {
    const requestHtml = JSON.stringify({
      filePath: path.join(__dirname, "fixtures", "file.html"),
      configFile
    });
    const responseHtml = await post(requestHtml, "/analyze");
    expect(JSON.parse(responseHtml)).toEqual([
      {
        line: 6,
        rule: "block-no-empty",
        text: "Unexpected empty block (block-no-empty)"
      }
    ]);
  });

  it("should cut BOM", async () => {
    const response = await post(
      JSON.stringify({
        filePath: path.join(__dirname, "fixtures", "file-bom.css"),
        configFile
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

  it("should return empty list of issues when request not json", async () => {
    const response = await post("invalid json", "/analyze");
    expect(JSON.parse(response)).toEqual([]);
    expect(errorSpy).toHaveBeenCalledWith(
      expect.objectContaining({
        message: expect.stringContaining(
          "Unexpected token i in JSON at position 0"
        )
      })
    );
  });

  it("should return empty list of issues when invalid request", async () => {
    const response = await post("{}", "/analyze");
    expect(JSON.parse(response)).toEqual([]);
    expect(errorSpy).toHaveBeenCalledWith(
      expect.objectContaining({
        message: expect.stringContaining(
          "Unexpected token i in JSON at position 0"
        )
      })
    );
  });

  it("should use fileContent from the request and not from the filesystem", async () => {
    const request = JSON.stringify({
      filePath: path.join(__dirname, "fixtures", "file.css"),
      fileContent: "\n\n a { }", // move the issue on line 3
      configFile
    });
    const response = await post(request, "/analyze");
    expect(JSON.parse(response)).toEqual([
      {
        line: 3,
        rule: "block-no-empty",
        text: "Unexpected empty block (block-no-empty)"
      }
    ]);
  });

  function post(data: string, endpoint: string): Promise<string> {
    return postToServer(data, endpoint, server);
  }
});

describe("server close", () => {
  it("should stop listening when closed", async () => {
    const server = await start();
    expect(server.listening).toBeTruthy();
    await postToServer("", "/close", server);
    expect(server.listening).toBeFalsy();
  });
});
