// Dev-Server-Proxy für die externen Datenquellen (CORS im Browser).
// Im Deployment übernimmt Apache dieselbe Aufgabe (siehe deploy/apache.conf).
const proxied = [
    { prefix: "/proxy/yahoo", target: "https://query1.finance.yahoo.com" },
    { prefix: "/proxy/nasdaqtrader", target: "https://www.nasdaqtrader.com" },
    { prefix: "/proxy/githubraw", target: "https://raw.githubusercontent.com" },
    { prefix: "/proxy/wikipedia", target: "https://en.wikipedia.org" },
];

config.devServer = Object.assign({}, config.devServer, {
    proxy: proxied.map((p) => ({
        context: [p.prefix],
        target: p.target,
        pathRewrite: { ["^" + p.prefix]: "" },
        changeOrigin: true,
        headers: { "User-Agent": "Mozilla/5.0" },
    })),
});
