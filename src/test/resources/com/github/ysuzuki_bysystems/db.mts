#!/usr/bin/env -S deno run --allow-env=PORT --allow-net --allow-read

import * as net from "node:net";
import process from "node:process";
import { PGlite } from "npm:@electric-sql/pglite@0.3.14";
import { fromNodeSocket } from "npm:pg-gateway@0.3.0-beta.4/node";

const port = Number.parseInt(process.env["PORT"] ?? "5432");

const db = new PGlite();
const server = net.createServer(async (sock) => {
  await fromNodeSocket(sock, {
    auth: {
      method: "trust",
    },
    async onStartup() {
      await db.waitReady;
    },
    async onMessage(data) {
      return await db.execProtocolRaw(data);
    },
  });
});
process.addListener("SIGTERM", () => void server.close());
server.listen(port, "127.0.0.1", () => {
  const addr = server.address();
  if (addr === null || typeof addr === "string") {
    throw new Error(`Unexpected: ${addr}`);
  }

  console.log(`BEGIN ${addr.address}:${addr.port}`);
});
