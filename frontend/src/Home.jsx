import React, { useState, useEffect } from 'react';
import recommendationService from './recommendationService.jsx';
import './Home.css'; // Vom crea acest fișier pentru stilizare

const Home = () => {
    const [cities, setCities] = useState([]);
    const [cityToView, setCityToView] = useState(null); // Orașul pentru care se afișează recomandări
    const [selectedDropdownCity, setSelectedDropdownCity] = useState(''); // Orașul selectat în dropdown
    const [recommendations, setRecommendations] = useState([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');

    // Efect pentru a încărca lista de orașe
    useEffect(() => {
        setLoading(true);
        recommendationService.getCities()
            .then(response => {
                setCities(response.data);
                setLoading(false);
            })
            .catch(error => {
                console.error('Failed to fetch cities:', error);
                setError('Could not load cities. Please try again later.');
                setLoading(false);
            });
    }, []);

    // Funcție pentru a gestiona trimiterea formularului de selecție
    const handleGetRecommendations = (e) => {
        e.preventDefault();
        if (!selectedDropdownCity) {
            setError('Please select a city from the list.');
            return;
        }
        setCityToView(selectedDropdownCity);
        setLoading(true);
        setError('');
        recommendationService.getRecommendationsForCity(selectedDropdownCity)
            .then(response => {
                setRecommendations(response.data);
                setLoading(false);
            })
            .catch(error => {
                console.error(`Failed to fetch recommendations for ${selectedDropdownCity}:`, error);
                setError(`Could not load recommendations for ${selectedDropdownCity}.`);
                setLoading(false);
            });
    };

    // Funcție pentru a reveni la lista de orașe
    const handleBackClick = () => {
        setCityToView(null);
        setRecommendations([]);
        setSelectedDropdownCity(''); // Resetează și selecția din dropdown
    };

    return (
        <div className="home-container">

            {error && <p className="error-message">{error}</p>}
            {loading && <p>Loading...</p>}

            {!cityToView ? (
                <div>

                    <form onSubmit={handleGetRecommendations} className="city-selector-form">
                        <select
                            value={selectedDropdownCity}
                            onChange={(e) => setSelectedDropdownCity(e.target.value)}
                            className="city-select"
                        >
                            <option value="">-- Select a City --</option>
                            {cities.map(city => (
                                <option key={city} value={city}>
                                    {city}
                                </option>
                            ))}
                        </select>
                        <button type="submit" className="get-recs-button">Get Recommendations</button>
                    </form>
                </div>
            ) : (
                <div>
                    <button onClick={handleBackClick} className="back-button">← Back to cities</button>
                    <h2>Recommendations for {cityToView}</h2>
                    <div className="recommendations-list">
                        {recommendations.length > 0 ? (
                            recommendations.map(rec => (
                                <div key={rec.id} className="recommendation-card">
                                    <h3>{rec.name}</h3>
                                    <p>{rec.description}</p>
                                </div>
                            ))
                        ) : (
                            <p>No recommendations found for this city.</p>
                        )}
                    </div>
                </div>
            )}
        </div>
    );
};

export default Home;
