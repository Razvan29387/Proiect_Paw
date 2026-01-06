import React, { useState, useEffect } from 'react';
import recommendationService from './recommendationService.jsx';
import { MapContainer, TileLayer, Marker, Popup, useMap, useMapEvents } from 'react-leaflet';
import 'leaflet/dist/leaflet.css';
import L from 'leaflet';
import './Home.css';
import SockJS from 'sockjs-client';
import { Stomp } from '@stomp/stompjs';

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

// Componentă pentru a gestiona click-urile pe hartă
function MapClickHandler({ onMapClick, onMapRightClick }) {
    useMapEvents({
        click: (e) => {
            onMapClick(e.latlng);
        },
        contextmenu: (e) => { // Click dreapta
            onMapRightClick(e);
        }
    });
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
    const [userPin, setUserPin] = useState(null); // Pin-ul pus de utilizator
    const [missedAttractions, setMissedAttractions] = useState([]); // Atracții ratate
    
    // Stare pentru notificări
    const [notifications, setNotifications] = useState([]);
    const [showNotifications, setShowNotifications] = useState(false);
    const [unreadCount, setUnreadCount] = useState(0);

    // Configurare WebSocket
    useEffect(() => {
        // Folosim o funcție factory pentru SockJS pentru a evita eroarea "Stomp.over did not receive a factory"
        const stompClient = new Stomp.Client({
            webSocketFactory: () => new SockJS('http://localhost:8080/ws'),
            reconnectDelay: 5000, // Încearcă reconectarea la fiecare 5 secunde
            onConnect: () => {
                console.log('Connected to WebSocket');
                stompClient.subscribe('/topic/recommendations', (message) => {
                    if (message.body) {
                        addNotification(message.body);
                    }
                });
            },
            onStompError: (frame) => {
                console.error('Broker reported error: ' + frame.headers['message']);
                console.error('Additional details: ' + frame.body);
            }
        });

        stompClient.activate();

        return () => {
            if (stompClient) {
                stompClient.deactivate();
            }
        };
    }, []);

    const addNotification = (message) => {
        const newNotification = {
            id: Date.now() + Math.random(), // ID unic mai robust
            text: message,
            read: false,
            timestamp: new Date().toLocaleTimeString()
        };
        setNotifications(prev => [newNotification, ...prev]);
        setUnreadCount(prev => prev + 1);
    };

    const deleteNotification = (id, e) => {
        e.stopPropagation(); // Previne închiderea dropdown-ului
        setNotifications(prev => prev.filter(n => n.id !== id));
        // Dacă notificarea ștearsă era necitită, scădem contorul (opțional, dar logic)
        // Deși la deschiderea dropdown-ului oricum resetăm contorul, e bine să fim consistenți
    };

    const clearAllNotifications = (e) => {
        e.stopPropagation();
        setNotifications([]);
        setUnreadCount(0);
    };

    const toggleNotifications = () => {
        setShowNotifications(!showNotifications);
        if (!showNotifications) {
            // Marchează toate ca citite când deschidem lista
            setUnreadCount(0);
            setNotifications(prev => prev.map(n => ({ ...n, read: true })));
        }
    };

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
        setUserPin(null);
        setMissedAttractions([]);
        
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
                    
                    // A. Geocodare (Photon e rapid, nu avem nevoie de delay artificial mare)
                    let coords = null;
                    try {
                        // Încercăm cu orașul inclus pentru precizie
                        coords = await geocodeLocation(`${rec.name}, ${searchQuery}`);
                        if (!coords) {
                             coords = await geocodeLocation(rec.name);
                        }
                        // Fallback 3: Încercăm fără virgulă
                        if (!coords) {
                            coords = await geocodeLocation(`${rec.name} ${searchQuery}`);
                        }
                        // Fallback 4: Încercăm să eliminăm prefixe comune
                        if (!coords) {
                            const cleanName = rec.name.replace(/^(The|Le|La|L')\s+/i, '');
                            if (cleanName !== rec.name) {
                                coords = await geocodeLocation(`${cleanName} ${searchQuery}`);
                            }
                        }
                    } catch (err) {
                        console.error(`Could not geocode ${rec.name}:`, err);
                    }

                    if (!coords) {
                        console.warn(`Failed to geocode: ${rec.name}`);
                    }

                    // B. Căutare Wikipedia (doar pentru atracții turistice)
                    let wikiData = null;
                    // Excludem explicit categoriile de cazare și masă
                    const excludedCategories = ['Restaurant', 'Guesthouse', 'Hotel'];
                    
                    if (!excludedCategories.includes(rec.category)) {
                        try {
                            // Trimitem numele atracției și orașul pentru validare
                            wikiData = await searchWikipedia(rec.name, searchQuery);
                        } catch (err) {
                            console.error(`Could not find Wikipedia link for ${rec.name}:`, err);
                        }
                    }

                    // Actualizăm starea marker-elor
                    if (coords) {
                        setMarkers(prev => [...prev, { 
                            ...rec, 
                            lat: coords.lat, 
                            lon: coords.lon, 
                            wikipediaLink: wikiData?.link,
                            imageUrl: wikiData?.imageUrl,
                            uniqueId: `${rec.name}-${i}` // ID unic pentru cheia React
                        }]);
                    }
                    
                    // Actualizăm și lista principală cu link-ul wiki găsit
                    if (wikiData) {
                        setRecommendations(prevRecs => 
                            prevRecs.map(r => r.name === rec.name ? { 
                                ...r, 
                                wikipediaLink: wikiData.link,
                                imageUrl: wikiData.imageUrl
                            } : r)
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
            // Folosim Photon API în loc de Nominatim
            const response = await fetch(`https://photon.komoot.io/api/?q=${encodeURIComponent(locationName)}&limit=1`);
            const data = await response.json();
            
            if (data && data.features && data.features.length > 0) {
                const coords = data.features[0].geometry.coordinates;
                // Photon returnează [lon, lat], Leaflet vrea [lat, lon]
                return { lat: coords[1], lon: coords[0] };
            }
            return null;
        } catch (error) {
            console.error("Geocoding error:", error);
            return null;
        }
    };

    const reverseGeocode = async (lat, lon) => {
        try {
            // Folosim Photon API pentru reverse geocoding
            const response = await fetch(`https://photon.komoot.io/reverse?lon=${lon}&lat=${lat}`);
            const data = await response.json();
            
            if (data && data.features && data.features.length > 0) {
                const props = data.features[0].properties;
                // Construim un nume relevant din proprietăți
                return props.name || props.street || props.city || "Unknown Location";
            }
            return null;
        } catch (error) {
            console.error("Reverse geocoding error:", error);
            return null;
        }
    };

    // Funcție îmbunătățită pentru căutare Wikipedia
    const searchWikipedia = async (placeName, cityName) => {
        // 1. Încercăm întâi cu numele exact al locului (cea mai mare precizie)
        let data = await fetchWikiData('en', placeName, placeName, cityName);
        
        // 2. Dacă nu găsim, încercăm "Place Name City Name"
        if (!data) {
            data = await fetchWikiData('en', `${placeName} ${cityName}`, placeName, cityName);
        }

        // 3. Fallback pe Română dacă nu găsim nimic în Engleză
        if (!data) {
             data = await fetchWikiData('ro', placeName, placeName, cityName);
        }

        return data;
    };

    const fetchWikiData = async (lang, searchTerm, originalName, cityName) => {
        try {
            // Cerem primele 5 rezultate
            const searchUrl = `https://${lang}.wikipedia.org/w/api.php?action=query&list=search&srsearch=${encodeURIComponent(searchTerm)}&srlimit=5&format=json&origin=*`;
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
                    const snippetLower = res.snippet.replace(/<[^>]*>?/gm, '').toLowerCase();
                    
                    // 1. Verificăm dacă titlul conține cuvinte din numele atracției
                    const hasNameMatch = nameKeywords.length === 0 || nameKeywords.some(k => titleLower.includes(k));
                    
                    // 2. VERIFICARE CRITICĂ: Orașul trebuie să apară în Titlu SAU în Snippet
                    // Relaxăm puțin condiția: dacă titlul este o potrivire foarte bună cu numele locului, acceptăm chiar dacă orașul nu e explicit în snippet
                    // (ex: "Louvre Museum" e suficient de unic, chiar dacă snippet-ul nu zice "Paris" imediat)
                    const isExactTitleMatch = titleLower.includes(originalName.toLowerCase());
                    const hasCityContext = titleLower.includes(cityLower) || snippetLower.includes(cityLower);

                    // 3. Excludem paginile de dezambiguizare
                    const isDisambiguation = titleLower.includes('disambiguation') || titleLower.includes('dezambiguizare');

                    return hasNameMatch && (hasCityContext || isExactTitleMatch) && !isDisambiguation;
                });

                if (relevantResults.length === 0) {
                    return null;
                }

                // Sortăm candidații validați după numărul de cuvinte (wordcount) descrescător
                // Dar prioritizăm titlurile care conțin numele exact al locului
                const sortedCandidates = relevantResults.sort((a, b) => {
                    const aExact = a.title.toLowerCase().includes(originalName.toLowerCase());
                    const bExact = b.title.toLowerCase().includes(originalName.toLowerCase());
                    
                    if (aExact && !bExact) return -1;
                    if (!aExact && bExact) return 1;
                    
                    return b.wordcount - a.wordcount;
                });

                const bestPage = sortedCandidates[0];
                
                // Acum cerem imaginea pentru pagina găsită
                const imageUrl = await fetchWikiImage(lang, bestPage.pageid);

                return {
                    link: `https://${lang}.wikipedia.org/wiki/${encodeURIComponent(bestPage.title)}`,
                    imageUrl: imageUrl
                };
            }
            return null;
        } catch (error) {
            return null;
        }
    };

    const fetchWikiImage = async (lang, pageId) => {
        try {
            // Cerem imaginea originală (sau una mare) în loc de thumbnail
            const url = `https://${lang}.wikipedia.org/w/api.php?action=query&prop=pageimages&pageids=${pageId}&piprop=original|thumbnail&pithumbsize=500&format=json&origin=*`;
            const response = await fetch(url);
            const data = await response.json();
            
            if (data.query && data.query.pages && data.query.pages[pageId]) {
                const page = data.query.pages[pageId];
                
                // 1. Încercăm imaginea originală
                if (page.original && page.original.source) {
                    if (isValidImage(page.original.source)) return page.original.source;
                }
                
                // 2. Încercăm thumbnail-ul
                if (page.thumbnail && page.thumbnail.source) {
                    if (isValidImage(page.thumbnail.source)) return page.thumbnail.source;
                }
            }
            return null;
        } catch (error) {
            return null;
        }
    };

    // Funcție helper pentru a filtra imaginile nedorite (iconițe, hărți, etc.)
    const isValidImage = (url) => {
        const lowerUrl = url.toLowerCase();
        // Excludem fișierele SVG (adesea iconițe) și fișierele care conțin cuvinte cheie suspecte
        if (lowerUrl.endsWith('.svg') || lowerUrl.endsWith('.svg.png')) return false;
        if (lowerUrl.includes('icon') || lowerUrl.includes('logo') || lowerUrl.includes('map') || lowerUrl.includes('location')) return false;
        return true;
    };

    const handleBackClick = () => {
        setCityToView(null);
        setRecommendations([]);
        setSearchQuery('');
        setMarkers([]);
        setMapZoom(6);
        setMapCenter([46.0, 25.0]);
        setUserPin(null);
        setMissedAttractions([]);
    };

    // Funcție pentru a calcula distanța dintre două puncte (Haversine formula)
    const calculateDistance = (lat1, lon1, lat2, lon2) => {
        const R = 6371; // Raza Pământului în km
        const dLat = (lat2 - lat1) * Math.PI / 180;
        const dLon = (lon2 - lon1) * Math.PI / 180;
        const a = 
            Math.sin(dLat/2) * Math.sin(dLat/2) +
            Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) * 
            Math.sin(dLon/2) * Math.sin(dLon/2);
        const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return R * c;
    };

    const handleMapClick = async (latlng) => {
        setUserPin(latlng);
        
        // 1. Găsim atracțiile din apropiere (ex: pe o rază de 2km) din lista existentă
        const nearby = markers.filter(marker => {
            const dist = calculateDistance(latlng.lat, latlng.lng, marker.lat, marker.lon);
            return dist <= 2.0; // 2 km
        });

        // NOTĂ: Nu mai setăm missedAttractions aici pentru a nu le afișa în lista de sub hartă
        // setMissedAttractions(nearby);

        // Adăugăm notificare locală pentru atracțiile ratate
        if (nearby.length > 0) {
            // Construim un mesaj detaliat cu lista atracțiilor
            const nearbyNames = nearby.map(m => m.name).join(', ');
            addNotification(`You are near ${nearby.length} attractions: ${nearbyNames}`);
        }

        // 2. Cerem AI-ului sugestii noi pentru această locație specifică
        addNotification("Searching for hidden gems near this location...");
        
        const locationName = await reverseGeocode(latlng.lat, latlng.lng);
        if (locationName && cityToView) {
            recommendationService.getSuggestionsForLocation(locationName, cityToView)
                .catch(err => console.error("Failed to get suggestions:", err));
        }
    };

    const handleMapRightClick = (e) => {
        // Ștergem pin-ul utilizatorului și resetăm atracțiile ratate
        setUserPin(null);
        setMissedAttractions([]);
    };

    // Funcție pentru a crea un icon personalizat cu imagine
    const createCustomIcon = (imageUrl) => {
        if (!imageUrl) return DefaultIcon;

        return new L.DivIcon({
            className: 'custom-marker-container',
            html: `<img src="${imageUrl}" class="custom-marker-image" />`,
            iconSize: [25, 41],
            iconAnchor: [12, 41],
            popupAnchor: [1, -34]
        });
    };

    return (
        <div className="home-container">
            <h1>Welcome to the AI-Powered Travel Guide</h1>
            
            {/* Clopoțel de notificări */}
            <div className="notification-bell-container">
                <div className="notification-bell" onClick={toggleNotifications}>
                    🔔
                    {unreadCount > 0 && <span className="notification-badge">{unreadCount}</span>}
                </div>
                {showNotifications && (
                    <div className="notification-dropdown">
                        <div className="notification-header" style={{padding: '10px', borderBottom: '1px solid #eee', display: 'flex', justifyContent: 'space-between', alignItems: 'center'}}>
                            <strong>Notifications</strong>
                            {notifications.length > 0 && (
                                <button onClick={clearAllNotifications} style={{fontSize: '0.8rem', padding: '2px 5px', cursor: 'pointer'}}>Clear All</button>
                            )}
                        </div>
                        {notifications.length === 0 ? (
                            <div className="no-notifications">No notifications</div>
                        ) : (
                            notifications.map(notif => (
                                <div key={notif.id} className={`notification-item ${!notif.read ? 'unread' : ''}`} style={{position: 'relative'}}>
                                    <button 
                                        onClick={(e) => deleteNotification(notif.id, e)} 
                                        style={{position: 'absolute', top: '5px', right: '5px', border: 'none', background: 'transparent', cursor: 'pointer', color: '#999'}}
                                    >
                                        ✕
                                    </button>
                                    <small>{notif.timestamp}</small><br/>
                                    {notif.text}
                                </div>
                            ))
                        )}
                    </div>
                )}
            </div>
            
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
                            <MapClickHandler onMapClick={handleMapClick} onMapRightClick={handleMapRightClick} />
                            <TileLayer
                                attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
                                url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                            />
                            {markers.map((marker, index) => (
                                <Marker 
                                    key={marker.uniqueId || index}
                                    position={[marker.lat, marker.lon]}
                                    icon={createCustomIcon(marker.imageUrl)}
                                >
                                    <Popup>
                                        <div className="popup-content">
                                            <strong>{marker.name}</strong><br />
                                            <span className="category-tag">{marker.category}</span><br />
                                            {marker.wikipediaLink && (
                                                <a href={marker.wikipediaLink} target="_blank" rel="noopener noreferrer">
                                                    Wikipedia
                                                </a>
                                            )}
                                        </div>
                                    </Popup>
                                </Marker>
                            ))}
                            {userPin && (
                                <Marker position={userPin} icon={new L.Icon({
                                    iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-red.png',
                                    shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/0.7.7/images/marker-shadow.png',
                                    iconSize: [25, 41],
                                    iconAnchor: [12, 41],
                                    popupAnchor: [1, -34],
                                    shadowSize: [41, 41]
                                })}>
                                    <Popup>Your Location</Popup>
                                </Marker>
                            )}
                        </MapContainer>
                    </div>

                    {/* Am scos lista de missed attractions de aici, ele apar acum doar în notificări */}

                    <div className="recommendations-list">
                        {recommendations.length > 0 ? (
                            recommendations.map((rec, index) => (
                                <div key={rec.id || index} className="recommendation-card">
                                    {/* Imaginea a fost scoasă de aici, conform cerinței */}
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
