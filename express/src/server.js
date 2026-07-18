import "dotenv/config";

import { createApp } from "./app.js";

const port = process.env.PORT || 3000;
createApp().listen(port, () => {
  console.log(`Inventory API (Express) on http://localhost:${port}`);
});
