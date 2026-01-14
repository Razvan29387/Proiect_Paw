import api from './api.jsx';

// Funcție pentru a prelua lista de orașe
const getCities = () => {
    return api.get('/Recommandations/cities');
};

// Funcție pentru a prelua recomandările pentru un oraș
const getRecommendationsForCity = (cityName) => {
    return api.get(`/Recommandations/${cityName}`);
};

// Funcție nouă pentru a cere sugestii specifice unei locații
const getSuggestionsForLocation = (locationName, cityName) => {
    return api.post(`/Recommandations/suggestions`, {
        locationName,
        cityName
    });
};

const recommendationService = {
    getCities,
    getRecommendationsForCity,
    getSuggestionsForLocation
};

export default recommendationService;
