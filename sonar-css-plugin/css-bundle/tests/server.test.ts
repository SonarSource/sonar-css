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

  beforeAll(() => {
    setLogHandlersForTests(logSpy, errorSpy);
  });

  beforeEach(async () => {
    server = await start();
    close = promisify(server.close.bind(server));
  });

  afterEach(async () => {
    jest.restoreAllMocks();
    await close();
  });

  it("should respond to analysis request", async () => {
    expect(server.listening).toEqual(true);
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
    expect(logSpy).toBeCalledTimes(2);
    expect(errorSpy).toBeCalledTimes(0);
  });

  it("should respond to analysis request for html and php", async () => {
    expect(server.listening).toEqual(true);
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
    expect(server.listening).toEqual(true);
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

  function post(data: string, endpoint: string): Promise<string> {
    return postToServer(data, endpoint, server);
  }
});
