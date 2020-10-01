import { Server } from "http";
import * as express from "express";
import { AddressInfo } from "net";
import * as stylelint from "stylelint";
import * as fs from "fs";
import * as bodyParser from "body-parser";

// for testing purposes
let log = console.log;
let logError = console.error;

const MAX_REQUEST_SIZE = "50mb";

export function setLogHandlersForTests(
  logHandler: typeof console.log,
  errorHandler: typeof console.error
) {
  log = logHandler;
  logError = errorHandler;
}

export function start(port = 0, host = "127.0.0.1"): Promise<Server> {
  return new Promise(resolve => {
    log("DEBUG starting stylelint-bridge server at port", port);
    const app = express();
    app.use(bodyParser.json({ limit: MAX_REQUEST_SIZE }));
    app.post("/analyze", analyzeWithStylelint);
    app.get("/status", (_: express.Request, resp: express.Response) =>
      resp.send("OK!")
    );

    app.post("/close", (_req: express.Request, resp: express.Response) => {
      console.log("DEBUG stylelint-bridge server will shutdown");
      resp.end(() => {
        server.close();
      });
    });

    // every time something is wrong we log error and send empty response (with 0 issues)
    // it's important to keep this call last in configuring "app"
    app.use(
      (
        error: any,
        _req: express.Request,
        response: express.Response,
        _next: any
      ) => processError(error, response)
    );

    const server = app.listen(port, host, () => {
      log(
        "DEBUG stylelint-bridge server is running at port",
        (server.address() as AddressInfo).port
      );
      resolve(server);
    });
  });
}

function analyzeWithStylelint(
  request: express.Request,
  response: express.Response
) {
  const parsedRequest = request.body as AnalysisInput;
  const { filePath, fileContent, configFile } = parsedRequest;
  const code =
    typeof fileContent == "string" ? fileContent : getFileContent(filePath);
  const options = {
    code,
    codeFilename: filePath,
    configFile
  };

  stylelint
    .lint(options)
    .then(result => response.json(toIssues(result.results, filePath)))
    .catch(error => processError(error, response));
}

function processError(error: any, response: express.Response) {
  logError(error);
  response.json([]);
}

function toIssues(results: stylelint.LintResult[], filePath: string): Issue[] {
  const analysisResponse: Issue[] = [];
  // we should have only one element in 'results' as we are analyzing only 1 file
  results.forEach(result => {
    // to avoid reporting on "fake" source like <input ccs 1>
    if (result.source !== filePath) {
      log(
        `DEBUG For file [${filePath}] received issues with [${result.source}] as a source. They will not be reported.`
      );
      return;
    }
    result.warnings.forEach(warning =>
      analysisResponse.push({
        line: warning.line,
        text: warning.text,
        rule: warning.rule
      })
    );
  });
  return analysisResponse;
}

function getFileContent(filePath: string) {
  const fileContent = fs.readFileSync(filePath, { encoding: "utf8" });
  // strip BOM
  if (fileContent.charCodeAt(0) === 0xfeff) {
    return fileContent.slice(1);
  }
  return fileContent;
}

export interface AnalysisInput {
  filePath: string;
  fileContent: string | undefined;
  configFile: string;
}

export interface Issue {
  line: number;
  rule: string;
  text: string;
}
