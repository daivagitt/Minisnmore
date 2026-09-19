module.exports = {
  launch_options: {
    args: ["--no-sandbox", "--disable-setuid-sandbox"],
  },
  stylesheet: [],
  css: `
    body {
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
      font-size: 11pt;
      line-height: 1.45;
      color: #1a1a1a;
      max-width: 52rem;
      margin: 2rem auto;
      padding: 0 1.5rem;
    }
    h1 { font-size: 1.75rem; border-bottom: 2px solid #e5e5e5; padding-bottom: 0.35rem; }
    h2 { font-size: 1.25rem; margin-top: 1.75rem; color: #333; }
    h3 { font-size: 1.05rem; }
    code, pre { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 0.9em; }
    pre { background: #f6f8fa; padding: 0.75rem 1rem; border-radius: 6px; overflow-x: auto; white-space: pre-wrap; }
    table { border-collapse: collapse; width: 100%; margin: 1rem 0; font-size: 0.95em; }
    th, td { border: 1px solid #ddd; padding: 0.45rem 0.6rem; text-align: left; vertical-align: top; }
    th { background: #f3f4f6; }
    a { color: #2563eb; }
    hr { border: none; border-top: 1px solid #e5e5e5; margin: 1.5rem 0; }
  `,
};
