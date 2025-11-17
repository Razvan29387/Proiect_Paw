import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App.jsx'; // Importăm componenta principală


// Aici "pornește" React-ul
ReactDOM.createRoot(document.getElementById('root')).render(
    <React.StrictMode>
        {/* Tot ce am scris noi (BrowserRouter, Routes, etc.)
      se află ÎN INTERIORUL componentei 'App'.
      Redând 'App' aici, pornim toată aplicația.
    */}
        <App />
    </React.StrictMode>
);