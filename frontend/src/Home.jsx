import React, { useState } from 'react';
import recommendationService from './recommendationService.jsx';
import { MapContainer, TileLayer, Marker, Popup, useMap } from 'react-leaflet';
import 'leaflet/dist/leaflet.css';
import L from 'leaflet';
import './Home.css';

// Fix pentru iconițele Leaflet care nu se încarcă corect în React
import icon from 'leaflet/dist/images/marker-icon.png';
import iconShadow from 'leaflet/dist/images/marker-shadow.png';

let DefaultIcon = L.icon({
    iconUrl: icon,
    shadowUrl: iconShadow,
    iconSize: [25, 41],
    iconAnchor: [12, 41]
});

L.Marker.prototype.options.icon = DefaultIcon;

// Componentă pentru a centra harta dinamic
function ChangeView({ center, zoom }) {
    const map = useMap();
    map.setView(center, zoom);
    return null;
}

const Home = () => {
    const [searchQuery, setSearchQuery] = useState('');
    const [cityToView, setCityToView] = useState(null);
    const [recommendations, setRecommendations] = useState([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    
    // Stare pentru hartă
    const [mapCenter, setMapCenter] = useState([46.0, 25.0]); // Centrul implicit (România)
    const [mapZoom, setMapZoom] = useState(6);
    const [markers, setMarkers] = useState([]);

    const handleSearch = (e) => {
        e.preventDefault();
        if (!searchQuery.trim()) {
            setError('Please enter a city name.');
            return;
        }
        setCityToView(searchQuery);
        setLoading(true);
        setError('');
        setMarkers([]); // Resetăm marker-ele
        
        recommendationService.getRecommendationsForCity(searchQuery)
            .then(async (response) => {
                const recs = response.data;
                setRecommendations(recs);
                setLoading(false);

                // 1. Găsim coordonatele orașului pentru a centra harta
                try {
                    const cityCoords = await geocodeLocation(searchQuery);
                    if (cityCoords) {
                        setMapCenter([cityCoords.lat, cityCoords.lon]);
                        setMapZoom(13);
                    }
                } catch (err) {
                    console.error("Could not geocode city:", err);
                }

                // 2. Procesăm fiecare recomandare: geocodare + căutare Wikipedia
                for (let i = 0; i < recs.length; i++) {
                    const rec = recs[i];
                    
                    // Delay pentru a nu suprasolicita API-urile
                    await new Promise(r => setTimeout(r, 1100));
                    
                    // A. Geocodare
                    let coords = null;
                    try {
                        // Încercăm cu orașul inclus pentru precizie
                        coords = await geocodeLocation(`${rec.name}, ${searchQuery}`);
                        if (!coords) {
                             await new Promise(r => setTimeout(r, 1100));
                             coords = await geocodeLocation(rec.name);
                        }
                    } catch (err) {
                        console.error(`Could not geocode ${rec.name}:`, err);
                    }

                    // B. Căutare Wikipedia (doar pentru atracții turistice)
                    let wikiLink = null;
                    // Excludem explicit categoriile de cazare și masă
                    const excludedCategories = ['Restaurant', 'Guesthouse', 'Hotel'];
                    
                    if (!excludedCategories.includes(rec.category)) {
                        try {
                            // Trimitem numele atracției și orașul pentru validare
                            wikiLink = await searchWikipedia(rec.name, searchQuery);
                        } catch (err) {
                            console.error(`Could not find Wikipedia link for ${rec.name}:`, err);
                        }
                    }

                    // Actualizăm starea marker-elor
                    if (coords) {
                        setMarkers(prev => [...prev, { ...rec, lat: coords.lat, lon: coords.lon, wikipediaLink: wikiLink }]);
                    }
                    
                    // Actualizăm și lista principală cu link-ul wiki găsit
                    if (wikiLink) {
                        setRecommendations(prevRecs => 
                            prevRecs.map(r => r.name === rec.name ? { ...r, wikipediaLink: wikiLink } : r)
                        );
                    }
                }
            })
            .catch(error => {
                console.error(`Failed to fetch recommendations for ${searchQuery}:`, error);
                
                if (error.response && error.response.data && error.response.data.message) {
                    setError(error.response.data.message);
                } else {
                    setError(`Could not load recommendations for ${searchQuery}.`);
                }
                
                setLoading(false);
                setCityToView(null);
            });
    };

    const geocodeLocation = async (locationName) => {
        try {
            const response = await fetch(`https://nominatim.openstreetmap.org/search?format=json&q=${encodeURIComponent(locationName)}`);
            const data = await response.json();
            if (data && data.length > 0) {
                return { lat: parseFloat(data[0].lat), lon: parseFloat(data[0].lon) };
            }
            return null;
        } catch (error) {
            console.error("Geocoding error:", error);
            return null;
        }
    };

    // Funcție îmbunătățită pentru căutare Wikipedia
    const searchWikipedia = async (placeName, cityName) => {
        // 1. Încercăm întâi pe Wikipedia în Engleză (en) - sursa principală acum
        // Căutăm "Place Name City Name" pentru precizie maximă
        let link = await fetchWikiLink('en', `${placeName} ${cityName}`, placeName, cityName);
        
        // 2. Dacă nu găsim, încercăm doar cu numele locului, dar validăm cu orașul
        if (!link) {
            link = await fetchWikiLink('en', placeName, placeName, cityName);
        }

        // 3. Fallback pe Română dacă nu găsim nimic în Engleză
        if (!link) {
             link = await fetchWikiLink('ro', `${placeName} ${cityName}`, placeName, cityName);
        }

        return link;
    };

    const fetchWikiLink = async (lang, searchTerm, originalName, cityName) => {
        try {
            // Cerem primele 10 rezultate pentru a avea o bază mai mare de selecție
            const searchUrl = `https://${lang}.wikipedia.org/w/api.php?action=query&list=search&srsearch=${encodeURIComponent(searchTerm)}&srlimit=10&format=json&origin=*`;
            const response = await fetch(searchUrl);
            const data = await response.json();
            
            if (data.query && data.query.search && data.query.search.length > 0) {
                const results = data.query.search;
                
                // Pregătim cuvintele cheie
                const nameKeywords = originalName.toLowerCase().split(' ').filter(w => w.length > 3);
                const cityLower = cityName.toLowerCase();

                // FILTRARE STRICTĂ
                const relevantResults = results.filter(res => {
                    const titleLower = res.title.toLowerCase();
                    // Curățăm snippet-ul de tag-uri HTML (ex: <span class="searchmatch">)
                    const snippetLower = res.snippet.replace(/<[^>]*>?/gm, '').toLowerCase();
                    
                    // 1. Verificăm dacă titlul conține cuvinte din numele atracției
                    const hasNameMatch = nameKeywords.length === 0 || nameKeywords.some(k => titleLower.includes(k));
                    
                    // 2. VERIFICARE CRITICĂ: Orașul trebuie să apară în Titlu SAU în Snippet (descriere scurtă)
                    // Aceasta previne confuziile între orașe (ex: Turnul Phoenix din Baia Mare vs Shanghai)
                    const hasCityContext = titleLower.includes(cityLower) || snippetLower.includes(cityLower);

                    return hasNameMatch && hasCityContext;
                });

                // Dacă nu avem niciun rezultat care să menționeze orașul, returnăm null
                if (relevantResults.length === 0) {
                    return null;
                }

                // Sortăm candidații validați după numărul de cuvinte (wordcount) descrescător
                const sortedCandidates = relevantResults.sort((a, b) => b.wordcount - a.wordcount);
                
                // Luăm titlul celui mai lung articol validat
                const bestPage = sortedCandidates[0];
                
                return `https://${lang}.wikipedia.org/wiki/${encodeURIComponent(bestPage.title)}`;
            }
            return null;
        } catch (error) {
            return null;
        }
    }

    const handleBackClick = () => {
        setCityToView(null);
        setRecommendations([]);
        setSearchQuery('');
        setMarkers([]);
        setMapZoom(6);
        setMapCenter([46.0, 25.0]);
    };

    return (
        <div className="home-container">
            <h1>Welcome to the AI-Powered Travel Guide</h1>
            
            {error && <p className="error-message">{error}</p>}
            {loading && <p>Loading recommendations, map data, and Wikipedia links...</p>}

            {!cityToView ? (
                <div>
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
                    </form>
                </div>
            ) : (
                <div>
                    <button onClick={handleBackClick} className="back-button">← New Search</button>
                    <h2>Recommendations for {cityToView}</h2>
                    
                    <div className="map-container" style={{ height: '400px', marginBottom: '20px' }}>
                        <MapContainer center={mapCenter} zoom={mapZoom} scrollWheelZoom={false} style={{ height: '100%', width: '100%' }}>
                            <ChangeView center={mapCenter} zoom={mapZoom} />
                            <TileLayer
                                attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
                                url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                            />
                            {markers.map((marker, index) => (
                                <Marker key={index} position={[marker.lat, marker.lon]}>
                                    <Popup>
                                        <strong>{marker.name}</strong><br />
                                        <span className="category-tag">{marker.category}</span><br />
                                        {marker.wikipediaLink && (
                                            <a href={marker.wikipediaLink} target="_blank" rel="noopener noreferrer">
                                                Wikipedia
                                            </a>
                                        )}
                                    </Popup>
                                </Marker>
                            ))}
                        </MapContainer>
                    </div>

                    <div className="recommendations-list">
                        {recommendations.length > 0 ? (
                            recommendations.map((rec, index) => (
                                <div key={rec.id || index} className="recommendation-card">
                                    <h3>{rec.name} <span className="category-tag">({rec.category})</span></h3>
                                    <p>{rec.description}</p>
                                    {rec.wikipediaLink && (
                                        <p>
                                            <a href={rec.wikipediaLink} target="_blank" rel="noopener noreferrer" className="wiki-link">
                                                Read more on Wikipedia
                                            </a>
                                        </p>
                                    )}
                                </div>
                            ))
                        ) : (
                            !loading && <p>No recommendations found for this city.</p>
                        )}
                    </div>
                </div>
            )}
        </div>
    );
};

export default Home;
