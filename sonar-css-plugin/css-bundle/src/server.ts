import { Server } from "http";
import * as express from "express";
import { AddressInfo } from "net";
import * as stylelint from "stylelint";
import * as fs from "fs";
import * as bodyParser from "body-parser";

let log = console.log;
let logError = console.error;

export function setLogHandlersForTests(
  logHandler: typeof console.log,
  errorHandler: typeof console.error
) {
  log = logHandler;
  logError = errorHandler;
}

export function start(port = 0): Promise<Server> {
  return new Promise(resolve => {
    log("DEBUG starting stylelint-bridge server at port", port);
    const app = express();
    app.use(bodyParser.json());
    app.post("/analyze", analyzeWithStylelint);
    app.get("/status", (_: express.Request, resp: express.Response) =>
      resp.send("OK!")
    );
    app.use(
      (err: any, _req: express.Request, res: express.Response, _next: any) => {
        logError(err);
        res.json([]);
      }
    );

    const server = app.listen(port, () => {
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
  const { filePath, configFile } = parsedRequest;
  const code = getFileContent(filePath);

  const options = {
    code,
    codeFilename: filePath,
    configFile
  };

  stylelint
    .lint(options)
    .then(result => response.json(toIssues(result.results, filePath)))
    .catch(error => {
      logError(error);
      response.json([]);
    });
}

function toIssues(results: stylelint.LintResult[], filePath: string): Issue[] {
  const analysisResponse: Issue[] = [];
  // we should have only one element in 'results' as we are analyzing only 1 file
  results.forEach(result => {
    // to avoid reporting of "fake" source like <input ccs 1>
    if (result.source !== filePath) {
      log(
        `DEBUG For file [${filePath}] received issues with [${result.source}] as a source.`
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
  return stripBom(fileContent);
}

function stripBom(s: string) {
  if (s.charCodeAt(0) === 0xfeff) {
    return s.slice(1);
  }
  return s;
}

export interface AnalysisInput {
  filePath: string;
  configFile: string;
}

export interface Issue {
  line: number;
  rule: string;
  text: string;
}
