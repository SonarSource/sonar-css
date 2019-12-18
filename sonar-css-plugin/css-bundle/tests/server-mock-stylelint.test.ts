import { start, setLogHandlersForTests } from "../src/server";
import { Server } from "http";
import * as path from "path";
import { promisify } from "util";
import * as stylelint from "stylelint";
import { postToServer } from "./utils";

const filePath = path.join(__dirname, "fixtures", "file.css");

const request = JSON.stringify({
  filePath,
  configFile: path.join(__dirname, "fixtures", "stylelintconfig.json")
});

jest.mock("stylelint");

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

  it("should not return issues for not original file", async () => {
    (stylelint.lint as any).mockResolvedValue({
      results: [{ source: "foo.bar" }]
    });
    const response = await postToServer(request, "/analyze", server);
    expect(JSON.parse(response)).toEqual([]);
    expect(logSpy).toHaveBeenCalledWith(
      `DEBUG For file [${filePath}] received issues with [foo.bar] as a source. They will not be reported.`
    );
  });

  it("should not return issues when failed promise returned", async () => {
    (stylelint.lint as any).mockRejectedValue("some reason");
    const response = await postToServer(request, "/analyze", server);
    expect(JSON.parse(response)).toEqual([]);
    expect(errorSpy).toHaveBeenCalledWith("some reason");
  });
});
