import { Server } from "http";
import * as express from "express";
import { AddressInfo } from "net";
import * as stylelint from "stylelint";
import * as fs from "fs";
import * as bodyParser from "body-parser";

export function start(port = 0): Promise<Server> {
  return new Promise(resolve => {
    console.log("DEBUG starting stylelint-bridge server at port", port);
    const app = express();
    app.use(bodyParser.json());
    app.post("/analyze", analyzeWithStylelint);
    app.get("/status", (_: express.Request, resp: express.Response) =>
      resp.send("OK!")
    );
    app.use((err: any, _req: express.Request, res: express.Response) => {
      //console.error(err);
      res.status(500).send([]);
    });

    const server = app.listen(port, () => {
      console.log(
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
  //throw new Error("Hey!!");
  try {
    const parsedRequest = request.body as AnalysisInput;
    let { filePath, configFile } = parsedRequest;
    const code = getFileContent(filePath);

    const options = {
      code,
      codeFilename: filePath,
      configFile
    };

    stylelint
      .lint(options)
      .then(result => response.json(toIssues(result.results)))
      .catch(error => {
        throw new Error(error);
      });
  } catch (e) {
    console.error(e.stack);
    response.json({ issues: [] });
  }
}

function toIssues(results: stylelint.LintResult[]): Issue[] {
  const analysisResponse: Issue[] = [];
  results.forEach(result =>
    result.warnings.forEach(warning =>
      analysisResponse.push({
        line: warning.line,
        text: warning.text,
        rule: warning.rule
      })
    )
  );
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
