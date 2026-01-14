import React, { useState, useEffect, useRef } from 'react';
import recommendationService from './recommendationService.jsx';
import { MapContainer, TileLayer, Marker, Popup, useMap, useMapEvents } from 'react-leaflet';
import 'leaflet/dist/leaflet.css';
import L from 'leaflet';
import './Home.css';
import LiveRecommendations from './components/LiveRecommendations.jsx';

import icon from 'leaflet/dist/images/marker-icon.png';
import iconShadow from 'leaflet/dist/images/marker-shadow.png';

const userIcon = new L.Icon({
    iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-red.png',
    shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/0.7.7/images/marker-shadow.png',
    iconSize: [25, 41],
    iconAnchor: [12, 41],
    popupAnchor: [1, -34],
    shadowSize: [41, 41]
});

let DefaultIcon = L.icon({
    iconUrl: icon,
    shadowUrl: iconShadow,
    iconSize: [25, 41],
    iconAnchor: [12, 41]
});

L.Marker.prototype.options.icon = DefaultIcon;

const createCustomIcon = (imageUrl) => {
    if (!imageUrl) return DefaultIcon;
    return L.divIcon({
        className: 'custom-marker-container',
        html: `<div class="custom-marker-pin"><img src="${imageUrl}" alt="marker" /></div>`,
        iconSize: [40, 40],
        iconAnchor: [20, 46],
        popupAnchor: [0, -42]
    });
};

function ChangeView({ center, zoom }) {
    const map = useMap();
    const lastCenter = useRef(center);
    useEffect(() => {
        if (center[0] !== lastCenter.current[0] || center[1] !== lastCenter.current[1]) {
            map.setView(center, zoom);
            lastCenter.current = center;
        }
    }, [center, zoom, map]);
    return null;
}

function LocationEvents({ onLocationSelect, onLocationClear }) {
    useMapEvents({
        click(e) { onLocationSelect(e.latlng); },
        contextmenu(e) { onLocationClear(); }
    });
    return null;
}

// Home primește acum stompClient și latestNotification de la App
const Home = ({ stompClient, latestNotification }) => {
    const [searchQuery, setSearchQuery] = useState('');
    const [cityToView, setCityToView] = useState(null);
    const [recommendations, setRecommendations] = useState([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [mapCenter, setMapCenter] = useState([48.0, 15.0]); 
    const [mapZoom, setMapZoom] = useState(4);
    const [markers, setMarkers] = useState([]);
    const [userLocation, setUserLocation] = useState(null);
    
    const currentSearchId = useRef(0);

    const handleReset = () => {
        setCityToView(null);
        setRecommendations([]);
        setSearchQuery('');
        setMarkers([]);
        setMapZoom(4);
        setMapCenter([48.0, 15.0]);
        setUserLocation(null);
        setError('');
    };

    const handleMapClick = async (latlng) => {
        setUserLocation(latlng);
        
        try {
            const response = await fetch(`https://nominatim.openstreetmap.org/reverse?format=json&lat=${latlng.lat}&lon=${latlng.lng}`);
            const data = await response.json();
            
            const address = data.address;
            const city = address.city || address.town || address.village || address.hamlet;
            const country = address.country;
            
            if (!cityToView && city) {
                const fullLocation = country ? `${city}, ${country}` : city;
                setSearchQuery(fullLocation);
            }
            
            // Folosim stompClient primit prin props
            if (stompClient && stompClient.connected) {
                const locationName = data.display_name.split(',')[0];
                stompClient.send("/app/updateLocation", {}, JSON.stringify({
                    city: city || "Unknown", locationName: locationName, latitude: latlng.lat, longitude: latlng.lng
                }));
            }
        } catch (err) {}
    };

    const handleMapRightClick = () => {
        setUserLocation(null);
    };

    const handleSearch = (e) => {
        e.preventDefault();
        if (!searchQuery.trim()) return;
        
        const searchId = Date.now();
        currentSearchId.current = searchId;

        setCityToView(searchQuery);
        setLoading(true);
        setError('');
        setMarkers([]);
        setRecommendations([]);

        recommendationService.getRecommendationsForCity(searchQuery)
            .then(async (response) => {
                if (currentSearchId.current !== searchId) return;

                const recs = response.data;
                setRecommendations(recs.map(r => ({...r, hasLocation: false})));
                setLoading(false);
                
                const cityData = await geocodeCity(searchQuery);
                if (currentSearchId.current !== searchId) return;

                let cityCoords = null;
                if (cityData) {
                    cityCoords = { lat: cityData.lat, lon: cityData.lon };
                    setMapCenter([cityData.lat, cityData.lon]);
                    setMapZoom(13);
                }

                for (const rec of recs) {
                    if (currentSearchId.current !== searchId) break;

                    await new Promise(r => setTimeout(r, 100));
                    
                    let wikiData = null;
                    if (rec.category === 'Tourist Attraction') {
                        wikiData = await searchWikipedia(rec.name, searchQuery, rec.englishName, cityCoords);
                    }

                    const geoData = await geocodeLocation(`${rec.name}, ${searchQuery}`);
                    let finalLat = geoData?.lat || wikiData?.lat || rec.lat;
                    let finalLon = geoData?.lon || wikiData?.lon || rec.lon;
                    let finalName = geoData?.officialName || rec.name;

                    if (cityCoords && finalLat && finalLon) {
                        const dist = calculateDistance(cityCoords.lat, cityCoords.lon, finalLat, finalLon);
                        if (dist > 20) {
                            finalLat = null;
                            finalLon = null;
                        }
                    }

                    if (currentSearchId.current === searchId) {
                        // MODIFICARE: Adăugăm descrierea în marker
                        const description = wikiData?.extract || rec.description || "No description available.";
                        
                        if (finalLat && finalLon) {
                            setMarkers(prev => [...prev, { 
                                ...rec, 
                                name: finalName, 
                                lat: finalLat, 
                                lon: finalLon, 
                                wikipediaLink: wikiData?.wikiUrl, 
                                imageUrl: wikiData?.imageUrl,
                                description: description // Salvăm descrierea
                            }]);
                            
                            setRecommendations(prevRecs => 
                                prevRecs.map(r => r.name === rec.name ? { 
                                    ...r, 
                                    name: finalName, 
                                    hasLocation: true,
                                    wikipediaLink: wikiData?.wikiUrl,
                                    description: description
                                } : r)
                            );
                        } else {
                            setRecommendations(prevRecs => 
                                prevRecs.map(r => r.name === rec.name ? { 
                                    ...r, 
                                    hasLocation: false,
                                    wikipediaLink: wikiData?.wikiUrl,
                                    description: description
                                } : r)
                            );
                        }
                    }
                }
            })
            .catch(err => {
                if (currentSearchId.current === searchId) {
                    setLoading(false);
                    setCityToView(null);
                    setError("Could not find recommendations for this city.");
                }
            });
    };

    const calculateDistance = (lat1, lon1, lat2, lon2) => {
        const R = 6371; 
        const dLat = (lat2 - lat1) * Math.PI / 180;
        const dLon = (lon2 - lon1) * Math.PI / 180;
        const a = Math.sin(dLat/2) * Math.sin(dLat/2) + Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) * Math.sin(dLon/2) * Math.sin(dLon/2);
        const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return R * c;
    };

    const geocodeCity = async (cityName) => {
        const res = await fetch(`https://photon.komoot.io/api/?q=${encodeURIComponent(cityName)}&limit=1`);
        const data = await res.json();
        if (data.features.length > 0) {
            const coords = data.features[0].geometry.coordinates;
            return { lat: coords[1], lon: coords[0] };
        }
        return null;
    };

    const geocodeLocation = async (q) => {
        try {
            const res = await fetch(`https://photon.komoot.io/api/?q=${encodeURIComponent(q)}&limit=1`);
            const data = await res.json();
            if (data.features.length > 0) {
                const props = data.features[0].properties;
                const coords = data.features[0].geometry.coordinates;
                return { lat: coords[1], lon: coords[0], officialName: props.name };
            }
        } catch (e) {}
        return null;
    };

    const hasWordOverlap = (str1, str2) => {
        if (!str1 || !str2) return false;
        const words1 = str1.toLowerCase().replace(/[^\w\s]/g, '').split(/\s+/).filter(w => w.length > 3);
        const words2 = str2.toLowerCase().replace(/[^\w\s]/g, '').split(/\s+/).filter(w => w.length > 3);
        return words1.some(w1 => words2.includes(w1));
    };

    const searchWikipedia = async (name, city, englishName, cityCoords) => {
        let res = await fetch(`https://ro.wikipedia.org/w/api.php?action=query&list=search&srsearch=${encodeURIComponent(name + " " + city)}&format=json&origin=*`);
        let data = await res.json();
        
        if (!data.query?.search?.length) {
             res = await fetch(`https://ro.wikipedia.org/w/api.php?action=query&list=search&srsearch=${encodeURIComponent(name)}&format=json&origin=*`);
             data = await res.json();
        }

        if (data.query?.search?.length > 0) {
            const candidates = data.query.search.slice(0, 3);
            const nameLower = name.toLowerCase();
            
            for (const candidate of candidates) {
                const titleLower = candidate.title.toLowerCase();
                if (titleLower === city.toLowerCase()) continue;

                const matchLocal = hasWordOverlap(name, candidate.title);
                const matchEnglish = englishName ? hasWordOverlap(englishName, candidate.title) : false;
                const matchReverse = candidate.title.toLowerCase().includes(name.toLowerCase());

                if (!matchLocal && !matchEnglish && !matchReverse) continue;

                const details = await fetchWikiDetails(candidate.title);
                const extractLower = details.extract ? details.extract.toLowerCase() : "";

                if (nameLower.includes("ortodox") && (titleLower.includes("catolic") || extractLower.includes("catolic") || extractLower.includes("reformat"))) continue;
                if (nameLower.includes("catolic") && (titleLower.includes("ortodox") || extractLower.includes("ortodox"))) continue;

                if (details.lat && details.lon && cityCoords) {
                    const dist = calculateDistance(cityCoords.lat, cityCoords.lon, details.lat, details.lon);
                    if (dist > 20) continue;
                }
                
                return { wikiUrl: `https://ro.wikipedia.org/wiki/${encodeURIComponent(candidate.title)}`, ...details };
            }
            
            let bestPage = data.query.search[0];
            if (bestPage.title.toLowerCase() === city.toLowerCase()) {
                if (data.query.search.length > 1) bestPage = data.query.search[1];
                else return null;
            }
            const details = await fetchWikiDetails(bestPage.title);
            if (details.lat && details.lon && cityCoords) {
                const dist = calculateDistance(cityCoords.lat, cityCoords.lon, details.lat, details.lon);
                if (dist > 20) return null;
            }
            return { wikiUrl: `https://ro.wikipedia.org/wiki/${encodeURIComponent(bestPage.title)}`, ...details };
        }
        return null;
    };

    const fetchWikiDetails = async (title) => {
        const res = await fetch(`https://ro.wikipedia.org/w/api.php?action=query&titles=${encodeURIComponent(title)}&prop=pageimages|coordinates|extracts&exintro=1&explaintext=1&format=json&pithumbsize=300&origin=*`);
        const data = await res.json();
        const page = Object.values(data.query.pages)[0];
        return {
            imageUrl: page.thumbnail?.source,
            lat: page.coordinates?.[0].lat,
            lon: page.coordinates?.[0].lon,
            extract: page.extract ? page.extract.substring(0, 200) + "..." : null
        };
    };

    return (
        <div className="home-container">
            <LiveRecommendations newNotification={latestNotification} />
            <div className="header-container">
                <h1>AI Travel Guide</h1>
                {/* Clopoțelul a fost mutat în Navbar */}
            </div>
            
            {error && <p className="error-message">{error}</p>}
            
            <div className="search-section">
                <h2>Search for a city to get recommendations:</h2>
                <form onSubmit={handleSearch} className="city-selector-form">
                    <input 
                        type="text" 
                        value={searchQuery} 
                        onChange={(e) => setSearchQuery(e.target.value)} 
                        placeholder="e.g., Paris, Tokyo, Rome" 
                        className="city-search-input" 
                    />
                    <button type="submit" className="get-recs-button">Search</button>
                    {cityToView && (
                        <button type="button" onClick={handleReset} className="reset-button" title="Reset Map">
                            🔄
                        </button>
                    )}
                </form>
            </div>

            {loading && <p>Loading...</p>}

            <div className="map-container">
                <MapContainer center={mapCenter} zoom={mapZoom} style={{ height: '100%' }}>
                    <ChangeView center={mapCenter} zoom={mapZoom} />
                    <TileLayer url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" />
                    <LocationEvents 
                        onLocationSelect={handleMapClick} 
                        onLocationClear={handleMapRightClick}
                    />
                    {userLocation && <Marker position={userLocation} icon={userIcon}><Popup>You are here!</Popup></Marker>}
                    {markers.map((m, i) => (
                        <Marker 
                            key={i} 
                            position={[m.lat, m.lon]} 
                            icon={createCustomIcon(m.imageUrl)}
                        >
                            <Popup className="custom-popup">
                                <div className="popup-content">
                                    <strong>{m.name}</strong><br/>
                                    {m.imageUrl && <img src={m.imageUrl} className="popup-image" alt="poi" />}
                                    
                                    {/* MODIFICARE: Afișăm descrierea trunchiată */}
                                    {m.description && (
                                        <p className="popup-description">
                                            {m.description.length > 100 
                                                ? m.description.substring(0, 100) + "..." 
                                                : m.description}
                                        </p>
                                    )}

                                    {m.category === 'Tourist Attraction' && m.wikipediaLink && (
                                        <div className="popup-link"><a href={m.wikipediaLink} target="_blank" rel="noopener noreferrer">Wikipedia</a></div>
                                    )}
                                </div>
                            </Popup>
                        </Marker>
                    ))}
                </MapContainer>
            </div>

            {recommendations.length > 0 && (
                <div className="recommendations-list">
                    {recommendations.map((rec, i) => (
                        <div key={i} className="recommendation-card">
                            <h3>
                                {rec.name} 
                                <span className="category-tag"> ({rec.category})</span>
                                {!rec.hasLocation && <span title="Location not found on map" style={{marginLeft: '10px', fontSize: '0.8em'}}>⚠️</span>}
                            </h3>
                            <p>{rec.description}</p>
                            {rec.category === 'Tourist Attraction' && rec.wikipediaLink && (
                                <a href={rec.wikipediaLink} target="_blank" rel="noopener noreferrer" className="wiki-link">Read on Wikipedia</a>
                            )}
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
};

export default Home;
