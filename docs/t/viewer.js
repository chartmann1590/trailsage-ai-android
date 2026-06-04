// Firebase Configuration for trailsage-ai-android-2026
const firebaseConfig = {
    apiKey: "__FIREBASE_API_KEY__",
    authDomain: "trailsage-ai-android-2026.firebaseapp.com",
    projectId: "trailsage-ai-android-2026",
    storageBucket: "trailsage-ai-android-2026.appspot.com",
    messagingSenderId: "843675203429",
    appId: "1:843675203429:web:d2be2f3f72159074097c35"
};

// Global variables
let map = null;
let routeLayer = null;
let markers = [];
let tripData = null;

// Initialize Web Page
document.addEventListener("DOMContentLoaded", () => {
    const urlParams = new URLSearchParams(window.location.search);
    const shareId = urlParams.get("t");
    
    if (!shareId) {
        showError("Invalid Link", "No trip sharing ID was specified in the URL query parameters.");
        return;
    }

    setupTabs();
    setupToggles();
    initFirebase(shareId);
});

// Setup sidebar tab switches
function setupTabs() {
    const tabStops = document.getElementById("tab-stops");
    const tabDirections = document.getElementById("tab-directions");
    const panelStops = document.getElementById("stops-panel");
    const panelDirections = document.getElementById("directions-panel");

    tabStops.addEventListener("click", () => {
        tabStops.classList.add("active");
        tabDirections.classList.remove("active");
        panelStops.classList.add("active");
        panelDirections.classList.remove("active");
    });

    tabDirections.addEventListener("click", () => {
        tabDirections.classList.add("active");
        tabStops.classList.remove("active");
        panelDirections.classList.add("active");
        panelStops.classList.remove("active");
    });
}

// Setup collapsible header and map controls
function setupToggles() {
    const toggleHeaderBtn = document.getElementById("toggle-header-btn");
    const sidebar = document.getElementById("sidebar");
    
    if (toggleHeaderBtn && sidebar) {
        toggleHeaderBtn.addEventListener("click", () => {
            const isCollapsed = sidebar.classList.toggle("header-collapsed");
            toggleHeaderBtn.textContent = isCollapsed ? "▲ Info" : "▼ Info";
        });
    }

    const toggleMapBtn = document.getElementById("toggle-map-btn");
    const appContainer = document.getElementById("app-container");
    
    if (toggleMapBtn && appContainer) {
        toggleMapBtn.addEventListener("click", () => {
            const isCollapsed = appContainer.classList.toggle("map-collapsed");
            toggleMapBtn.textContent = isCollapsed ? "🗺️ Show Map" : "🗺️ Minimize Map";
            
            // Force Leaflet to recalculate map size after layout transition completes
            setTimeout(() => {
                if (map) {
                    map.invalidateSize({ animate: true });
                }
            }, 300);
        });
    }
}

// Initialize Firebase and Fetch Trip
function initFirebase(shareId) {
    try {
        firebase.initializeApp(firebaseConfig);
        const db = firebase.firestore();
        
        db.collection("shared_trips").document = db.collection("shared_trips").doc; // compat normalization
        
        db.collection("shared_trips").doc(shareId).get()
            .then((doc) => {
                if (doc.exists) {
                    tripData = doc.data();
                    renderTrip(shareId, tripData);
                } else {
                    showError("Trip Not Found", "The requested shared trip does not exist in the database. It might have been deleted or expired.");
                }
            })
            .catch((error) => {
                console.error("Firebase read error: ", error);
                showError("Database Error", "Failed to retrieve shared trip data from the cloud: " + error.message);
            });
    } catch (e) {
        console.error("Firebase initialization failed: ", e);
        showError("Initialization Error", "Failed to load cloud libraries. Check your internet connection.");
    }
}

// Render trip data to UI
function renderTrip(shareId, data) {
    // Populate header info
    document.getElementById("trip-title").textContent = data.name || "Custom Trip";
    document.getElementById("trip-desc").textContent = data.description || "No description provided.";
    
    const stopCount = data.stops ? data.stops.length : 0;
    const driveTime = data.estimatedDriveMinutes || 0;
    document.getElementById("stop-count").textContent = stopCount;
    document.getElementById("drive-time").textContent = driveTime;
    document.getElementById("trip-meta").classList.remove("hidden");

    // Configure open app deep link
    const appBtn = document.getElementById("open-app-btn");
    appBtn.href = `trailsage://trip?t=${shareId}`;
    appBtn.classList.remove("hidden");

    // Setup Leaflet map
    initMap(data);

    // Populate Lists
    renderStops(data.stops || []);
    renderDirections(data.directions || []);

    // Remove loading screen on map
    const mapLoader = document.getElementById("map-loader");
    if (mapLoader) {
        mapLoader.style.opacity = '0';
        setTimeout(() => mapLoader.classList.add("hidden"), 500);
    }
}

// Setup Leaflet Map, Route Polyline and Markers
function initMap(data) {
    // Standard Dark map style (no API key needed)
    map = L.map("map", {
        zoomControl: false
    }).setView([40, -75], 8);
    
    L.control.zoom({ position: 'topright' }).addTo(map);

    L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors &copy; <a href="https://carto.com/attributions">CARTO</a>',
        subdomains: 'abcd',
        maxZoom: 20
    }).addTo(map);

    // Render Route GeoJSON
    if (data.routeGeoJson) {
        try {
            const geoJsonData = JSON.parse(data.routeGeoJson);
            routeLayer = L.geoJSON(geoJsonData, {
                style: {
                    color: '#4e82f7',
                    weight: 5,
                    opacity: 0.85
                }
            }).addTo(map);
            
            // Zoom map to fit route
            map.fitBounds(routeLayer.getBounds(), { padding: [50, 50] });
        } catch (e) {
            console.error("Failed to parse route GeoJSON:", e);
        }
    }

    // Render Stop Markers
    if (data.stops && data.stops.length > 0) {
        data.stops.forEach((stop, index) => {
            const lat = stop.latitude;
            const lon = stop.longitude;
            if (lat && lon) {
                const icon = L.divIcon({
                    className: 'custom-marker',
                    html: `<span>${index + 1}</span>`,
                    iconSize: [26, 26],
                    iconAnchor: [13, 13]
                });

                const marker = L.marker([lat, lon], { icon }).addTo(map);
                marker.bindPopup(`<b>${stop.title}</b><br>Stop #${index + 1}`);
                
                // Track markers
                markers.push({
                    index: index,
                    marker: marker,
                    lat: lat,
                    lon: lon
                });
            }
        });

        // If no route layer was found, fit map around markers
        if (!routeLayer && markers.length > 0) {
            const group = L.featureGroup(markers.map(m => m.marker));
            map.fitBounds(group.getBounds().pad(0.1));
        }
    }
}

// Render Stop Cards list
function renderStops(stops) {
    const list = document.getElementById("stops-list");
    list.innerHTML = "";

    if (stops.length === 0) {
        list.innerHTML = '<div class="card"><p class="card-preview">No stops in this trip.</p></div>';
        return;
    }

    stops.forEach((stop, index) => {
        const card = document.createElement("div");
        card.className = "card";
        
        let imageHtml = "";
        // If image is a remote URL, render it
        if (stop.imageLocalPath && stop.imageLocalPath.startsWith("http")) {
            imageHtml = `<img class="card-image" src="${stop.imageLocalPath}" alt="${stop.title}" loading="lazy">`;
        }

        let aiBadge = stop.generatedByAi ? '<span class="ai-badge">AI</span>' : '';

        // Build sources html
        let sourcesHtml = "";
        if (stop.sources && stop.sources.length > 0) {
            sourcesHtml = `
                <div class="detail-section">
                    <div class="detail-title">Sources</div>
                    <div class="sources-container">
                        ${stop.sources.map(s => `
                            <a href="${s.url || '#'}" target="_blank" class="source-tag">${s.title} (${s.license || 'Public'})</a>
                        `).join('')}
                    </div>
                </div>
            `;
        }

        card.innerHTML = `
            <div class="card-header">
                <div class="card-index">${index + 1}</div>
                <div class="card-title">${stop.title}</div>
                ${aiBadge}
            </div>
            ${imageHtml}
            <div class="card-preview">${stop.description || ""}</div>
            
            <div class="card-detail">
                <div class="detail-section">
                    <div class="detail-title">Narration</div>
                    <div class="detail-text">${stop.narration || "No narration text."}</div>
                </div>
                ${stop.funFact ? `
                <div class="detail-section">
                    <div class="detail-title">Did You Know?</div>
                    <div class="detail-text"><i>${stop.funFact}</i></div>
                </div>` : ''}
                ${sourcesHtml}
            </div>
        `;

        // Pan to stop on card click
        card.addEventListener("click", () => {
            const markerData = markers.find(m => m.index === index);
            if (markerData && map) {
                // Reset other markers
                markers.forEach(m => {
                    m.marker.getElement()?.classList.remove("active");
                });
                
                // Set active class
                markerData.marker.getElement()?.classList.add("active");
                
                // Pan map
                map.setView([markerData.lat, markerData.lon], 15, {
                    animate: true,
                    duration: 0.8
                });
                markerData.marker.openPopup();
            }
        });

        list.appendChild(card);
    });
}

// Render Directions list
function renderDirections(directions) {
    const list = document.getElementById("directions-list");
    list.innerHTML = "";

    if (directions.length === 0) {
        list.innerHTML = '<div class="card"><p class="card-preview">No turn-by-turn directions found. You can generate directions inside the TrailSage app.</p></div>';
        return;
    }

    directions.forEach((step, index) => {
        const div = document.createElement("div");
        div.className = "direction-card";
        
        div.innerHTML = `
            <span class="direction-index">${index + 1}.</span>
            <span class="direction-text">${step.text}</span>
        `;
        
        // Pan map to maneuver location on click
        if (step.lat && step.lon) {
            div.style.cursor = "pointer";
            div.addEventListener("click", () => {
                if (map) {
                    map.setView([step.lat, step.lon], 16, {
                        animate: true,
                        duration: 0.8
                    });
                }
            });
        }

        list.appendChild(div);
    });
}

// Display error modal
function showError(title, message) {
    document.getElementById("error-message").textContent = message;
    document.querySelector(".error-card h2").textContent = title;
    document.getElementById("error-overlay").classList.remove("hidden");
    
    const mapLoader = document.getElementById("map-loader");
    if (mapLoader) {
        mapLoader.classList.add("hidden");
    }
}
