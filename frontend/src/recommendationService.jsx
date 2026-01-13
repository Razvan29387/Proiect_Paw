import axios from 'axios';

// CORECȚIE: Folosim 'Recommandations' cu 'R' mare pentru a se potrivi cu backend-ul
const API_URL = 'http://localhost:8080/api/v1/Recommandations';

// Funcție pentru a prelua lista de orașe
const getCities = () => {
    return axios.get(API_URL + '/cities');
};

// Funcție pentru a prelua recomandările pentru un oraș
const getRecommendationsForCity = (cityName) => {
    return axios.get(`${API_URL}/${cityName}`);
};

// Funcție nouă pentru a cere sugestii specifice unei locații
const getSuggestionsForLocation = (locationName, cityName) => {
    return axios.post(`${API_URL}/suggestions`, {
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
