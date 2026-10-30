import http from "node:http";

const username = process.env.PORTAL_PROXY_USER;
const password = process.env.PORTAL_PROXY_PASSWORD;
if (!username || !password) throw new Error("Proxy credentials are required");

const expected = `Basic ${Buffer.from(`${username}:${password}`).toString("base64")}`;
const server = http.createServer((request, response) => {
  if (request.headers.authorization !== expected) {
    response.writeHead(401, {
      "Cache-Control": "no-store",
      "WWW-Authenticate": 'Basic realm="Penny Drop QA Portal", charset="UTF-8"',
    });
    response.end("Authentication required");
    return;
  }

  const headers = { ...request.headers };
  delete headers.authorization;
  headers.host = "127.0.0.1:8080";
  if (headers.origin) headers.origin = "http://127.0.0.1:8080";
  if (headers.referer) headers.referer = "http://127.0.0.1:8080/";

  const upstream = http.request(
    {
      hostname: "127.0.0.1",
      port: 8080,
      path: request.url,
      method: request.method,
      headers,
    },
    (upstreamResponse) => {
      response.writeHead(upstreamResponse.statusCode ?? 502, upstreamResponse.headers);
      upstreamResponse.pipe(response);
    },
  );
  upstream.on("error", () => {
    if (!response.headersSent) response.writeHead(502, { "Content-Type": "text/plain" });
    response.end("Portal is unavailable");
  });
  request.pipe(upstream);
});

server.listen(8090, "127.0.0.1", () => {
  console.log("Authenticated portal proxy ready on http://127.0.0.1:8090");
});
