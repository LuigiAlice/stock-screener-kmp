const PAGE_ORIGIN = "https://luigialice.github.io";
const LOCAL_ORIGINS = new Set([
  "http://localhost:8080",
  "http://127.0.0.1:8080",
]);

const SOURCES = {
  yahoo: {
    host: "query1.finance.yahoo.com",
    cacheTtl: 300,
    matches: (path) => /^v8\/finance\/chart\/[A-Za-z0-9.^=_-]{1,20}$/.test(path),
  },
  nasdaqtrader: {
    host: "www.nasdaqtrader.com",
    cacheTtl: 86_400,
    matches: (path) =>
      path === "dynamic/SymDir/nasdaqlisted.txt" ||
      path === "dynamic/SymDir/otherlisted.txt",
  },
  githubraw: {
    host: "raw.githubusercontent.com",
    cacheTtl: 86_400,
    matches: (path) =>
      path === "datasets/s-and-p-500-companies/main/data/constituents.csv",
  },
  wikipedia: {
    host: "en.wikipedia.org",
    cacheTtl: 21_600,
    matches: (path) =>
      path === "wiki/List_of_NASDAQ-100_companies" ||
      path === "wiki/List_of_Dow_Jones_Industrial_Average_companies" ||
      path === "wiki/DAX" ||
      path === "wiki/MDAX",
  },
};

function allowedOrigin(origin) {
  return origin === PAGE_ORIGIN || LOCAL_ORIGINS.has(origin);
}

function corsHeaders(origin, request) {
  return {
    "Access-Control-Allow-Origin": origin,
    "Access-Control-Allow-Methods": "GET, OPTIONS",
    // Angefragte Header übernehmen: WebKit/Safari löst schon bei gesetzten
    // Standard-Headern eine Preflight aus; eine feste Liste blockiert dann.
    "Access-Control-Allow-Headers":
      request.headers.get("Access-Control-Request-Headers") ?? "Accept, Content-Type",
    "Access-Control-Max-Age": "86400",
    Vary: "Origin",
  };
}

function errorResponse(status, message, origin, request) {
  return new Response(message, {
    status,
    headers: {
      ...corsHeaders(origin, request),
      "Content-Type": "text/plain; charset=utf-8",
      "Cache-Control": "no-store",
    },
  });
}

function targetFor(requestUrl) {
  const match = /^\/proxy\/([a-z]+)\/(.*)$/.exec(requestUrl.pathname);
  if (!match) return null;

  let path;
  try {
    path = decodeURIComponent(match[2]);
  } catch {
    return null;
  }

  if (!path || path.includes("\\") || path.split("/").includes("..")) return null;

  const source = SOURCES[match[1]];
  if (!source || !source.matches(path)) return null;

  const target = new URL(`https://${source.host}/${path}`);
  if (match[1] === "yahoo") {
    const allowedQuery = new Set([
      "range",
      "interval",
      "period1",
      "period2",
      "events",
      "includePrePost",
    ]);
    for (const [key, value] of requestUrl.searchParams) {
      if (allowedQuery.has(key) && value.length <= 32) {
        target.searchParams.append(key, value);
      }
    }
  }

  return { source, target };
}

export default {
  async fetch(request) {
    const origin = request.headers.get("Origin") ?? "";
    if (!allowedOrigin(origin)) {
      return new Response("Origin not allowed", { status: 403 });
    }

    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: corsHeaders(origin, request) });
    }
    if (request.method !== "GET") {
      return errorResponse(405, "Method not allowed", origin, request);
    }

    const route = targetFor(new URL(request.url));
    if (!route) return errorResponse(404, "Data source or path not allowed", origin, request);

    let upstream;
    try {
      upstream = await fetch(route.target, {
        headers: {
          Accept: request.headers.get("Accept") ?? "*/*",
          "User-Agent": "Mozilla/5.0 (compatible; StockScreener/1.0)",
        },
        redirect: "manual",
        cf: { cacheTtl: route.source.cacheTtl, cacheEverything: true },
      });
    } catch {
      return errorResponse(502, "Data source is unavailable", origin, request);
    }

    if (upstream.status >= 300 && upstream.status < 400) {
      return errorResponse(502, "Data source redirected unexpectedly", origin, request);
    }

    return new Response(upstream.body, {
      status: upstream.status,
      headers: {
        ...corsHeaders(origin, request),
        "Content-Type": upstream.headers.get("Content-Type") ?? "application/octet-stream",
        "Cache-Control": `public, max-age=${route.source.cacheTtl}`,
      },
    });
  },
};
