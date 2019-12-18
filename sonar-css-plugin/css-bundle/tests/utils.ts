import * as http from "http";
import { Server } from "http";
import { AddressInfo } from "net";

export function postToServer(
  data: string,
  endpoint: string,
  server: Server
): Promise<string> {
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
