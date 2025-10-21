<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Hiking Utility App - README</title>
  <style>
    body {
      font-family: "Segoe UI", Roboto, Arial, sans-serif;
      background: #f9fafb;
      color: #222;
      line-height: 1.6;
      margin: 0;
      padding: 2rem;
    }
    h1, h2, h3 {
      color: #1b4d3e;
    }
    pre {
      background: #f3f4f6;
      padding: 10px;
      border-radius: 5px;
      overflow-x: auto;
    }
    code {
      color: #b91c1c;
      font-weight: bold;
    }
    .section {
      background: #fff;
      padding: 1.5rem;
      border-radius: 10px;
      margin-bottom: 2rem;
      box-shadow: 0 2px 6px rgba(0,0,0,0.05);
    }
    .screenshots {
      display: flex;
      flex-wrap: wrap;
      gap: 10px;
    }
    .screenshot {
      width: 250px;
      height: 520px;
      background: #e5e7eb;
      border: 2px dashed #9ca3af;
      border-radius: 8px;
      display: flex;
      align-items: center;
      justify-content: center;
      color: #6b7280;
      font-size: 14px;
    }
  </style>
</head>
<body>

  <h1>🥾 Hiking Utility App</h1>
  <p>An Android hiking companion app that provides GPS tracking, GPX route management, and offline map support — powered by <strong>MapLibre</strong> and <strong>MapTiler</strong>.</p>

  <div class="section">
    <h2>🚀 Features</h2>
    <ul>
      <li><strong>Live GPS Tracking:</strong> Record distance, time, and pace in real-time.</li>
      <li><strong>Interactive Maps:</strong> View routes and your position with MapLibre.</li>
      <li><strong>GPX Support:</strong> Import/export GPX files for hikes or trails.</li>
      <li><strong>Offline Maps:</strong> Download and manage maps for areas without internet.</li>
      <li><strong>Simple UI:</strong> One-tap access to map, tracking, and offline tools.</li>
    </ul>
  </div>

  <div class="section">
    <h2>📱 Screenshots</h2>
    <div class="screenshots">
      <div class="screenshot">Main Screen Placeholder</div>
      <div class="screenshot">Map View Placeholder</div>
      <div class="screenshot">Tracking Screen Placeholder</div>
      <div class="screenshot">Offline Map Manager Placeholder</div>
    </div>
    <p style="color:#666; font-size:0.9rem;">Replace the placeholders above with your actual app screenshots (recommended width: 250px each).</p>
  </div>

  <div class="section">
    <h2>⚙️ Setup & Installation</h2>
    <ol>
      <li><strong>Clone the Repository</strong>
        <pre><code>git clone https://github.com/daveeeeeehike/HikingUtility.git
cd HikingUtility</code></pre>
      </li>

      <li><strong>Open in Android Studio</strong><br>
        File → Open → Select this project folder.</li>

      <li><strong>Add Your MapTiler API Key</strong>
        <p>Open the following files and replace the placeholder value:</p>
        <pre><code>// MapActivity.java
private static final String MAPTILER_KEY = "******";

// OfflineMapDownloader.java
private static final String MAPTILER_KEY = "******";
</code></pre>
        <p>Replace <code>******</code> with your actual MapTiler key from <a href="https://cloud.maptiler.com/" target="_blank">https://cloud.maptiler.com/</a>.</p>
      </li>

      <li><strong>Run the App</strong><br>
        Connect your device or emulator, then click ▶️ “Run”.</li>
    </ol>
  </div>

  <div class="section">
    <h2>📜 Permissions</h2>
    <ul>
      <li><code>ACCESS_FINE_LOCATION</code> — precise GPS tracking</li>
      <li><code>ACCESS_COARSE_LOCATION</code> — approximate location access</li>
    </ul>
    <p>Make sure to grant these permissions when prompted.</p>
  </div>

  <div class="section">
    <h2>🧭 Offline Maps</h2>
    <p>Use the <strong>Offline Map Downloader</strong> to download the current visible map region for offline access.  
    Manage or delete offline maps through the <strong>Offline Map Manager</strong>.</p>
  </div>

  <div class="section">
    <h2>🧰 Tech Stack</h2>
    <ul>
      <li>Java (Android)</li>
      <li>MapLibre SDK</li>
      <li>MapTiler Maps API</li>
      <li>GPX XML Parsing</li>
      <li>Android Services & Permissions</li>
    </ul>
  </div>

  <div class="section">
    <h2>👤 Author</h2>
    <p><strong>Dave Hike</strong><br>
    Built with ❤️ for hikers, explorers, and Android developers.<br>
    <a href="https://maplibre.org" target="_blank">MapLibre</a> | 
    <a href="https://docs.maptiler.com" target="_blank">MapTiler Docs</a></p>
  </div>

</body>
</html>
