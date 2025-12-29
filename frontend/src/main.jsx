import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom'; // Importăm BrowserRouter
import App from './App.jsx';

// Aici "pornește" React-ul
ReactDOM.createRoot(document.getElementById('root')).render(
    <React.StrictMode>
        {/* CORECȚIE: Înfășurăm întreaga aplicație în BrowserRouter */}
        <BrowserRouter>
            <App />
        </BrowserRouter>
    </React.StrictMode>
);
