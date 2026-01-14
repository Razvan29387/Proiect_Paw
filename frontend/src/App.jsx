import React, { useState, useEffect, useRef } from 'react';
import { Routes, Route, Link, useNavigate, Navigate } from 'react-router-dom';
import Login from './Login.jsx';
import Register from './Register.jsx';
import Home from './Home.jsx';
import authService from './authService.jsx';
import SockJS from 'sockjs-client';
import Stomp from 'stompjs';
import './App.css';

const PrivateRoute = ({ children, isLoggedIn }) => {
    return isLoggedIn ? children : <Navigate to="/login" />;
};

function App() {
    const navigate = useNavigate();
    const [isLoggedIn, setIsLoggedIn] = useState(false);
    
    // Stare pentru notificări
    const [notificationsHistory, setNotificationsHistory] = useState([]);
    const [showNotifications, setShowNotifications] = useState(false);
    const [unreadCount, setUnreadCount] = useState(0);
    const [latestNotification, setLatestNotification] = useState(null);
    const stompClientRef = useRef(null);
    const isConnecting = useRef(false);

    // Gestionare WebSocket la nivel global
    useEffect(() => {
        if (!isLoggedIn) {
            if (stompClientRef.current && stompClientRef.current.connected) {
                stompClientRef.current.disconnect();
                stompClientRef.current = null;
            }
            return;
        }

        if (stompClientRef.current?.connected || isConnecting.current) return;

        isConnecting.current = true;
        const socket = new SockJS('http://localhost:8080/ws');
        const stompClient = Stomp.over(socket);
        stompClient.debug = null;

        stompClient.connect({}, () => {
            console.log('App: Connected to WebSocket');
            isConnecting.current = false;
            stompClientRef.current = stompClient;

            stompClient.subscribe('/topic/alerts', (message) => {
                if (message.body) {
                    // Generăm un ID unic combinând timpul cu un număr random
                    const uniqueId = `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
                    
                    const newNotif = {
                        id: uniqueId,
                        text: message.body
                    };
                    
                    setNotificationsHistory(prev => [newNotif, ...prev]);
                    setUnreadCount(prev => prev + 1);
                    setLatestNotification({ text: message.body, timestamp: Date.now() });
                }
            });
        }, (error) => {
            console.error('App: WebSocket connection error:', error);
            isConnecting.current = false;
        });

        return () => {
            // Nu deconectăm la unmount-ul App, ci doar la logout
        };
    }, [isLoggedIn]);

    const handleLogout = () => {
        authService.logout().finally(() => {
            setIsLoggedIn(false);
            setNotificationsHistory([]);
            setUnreadCount(0);
            if (stompClientRef.current) {
                stompClientRef.current.disconnect();
                stompClientRef.current = null;
            }
            navigate('/login');
        });
    };

    const handleLoginSuccess = () => {
        setIsLoggedIn(true);
        navigate('/home');
    };

    const toggleNotifications = () => {
        setShowNotifications(!showNotifications);
        if (!showNotifications) setUnreadCount(0);
    };

    const clearNotifications = () => {
        setNotificationsHistory([]);
        setUnreadCount(0);
        setShowNotifications(false);
    };

    const removeNotification = (id, e) => {
        e.stopPropagation();
        // Filtrăm corect pe baza ID-ului
        setNotificationsHistory(prev => prev.filter(n => (n.id || n) !== id));
    };

    return (
        <div className="app-container">
            <nav className="main-nav">
                <div className="nav-brand">TravelAI</div>
                <ul className="nav-links">
                    {isLoggedIn && <li><Link to="/home">Home</Link></li>}
                    
                    {isLoggedIn && (
                        <li className="notification-bell-container">
                            <button className="notification-bell-nav" onClick={toggleNotifications}>
                                🔔{unreadCount > 0 && <span className="notification-badge-nav">{unreadCount}</span>}
                            </button>
                            {showNotifications && (
                                <div className="notifications-dropdown-nav">
                                    <div className="notifications-header">
                                        <h3>Notifications</h3>
                                        {notificationsHistory.length > 0 && (
                                            <button onClick={clearNotifications} className="clear-notifications-btn">Clear All</button>
                                        )}
                                    </div>
                                    {notificationsHistory.length > 0 ? (
                                        <ul>
                                            {notificationsHistory.map((notif, index) => {
                                                // Verificăm dacă notificarea e obiect nou sau string vechi
                                                const isObject = typeof notif === 'object' && notif !== null;
                                                const text = isObject ? notif.text : notif;
                                                const id = isObject ? notif.id : notif; // Folosim textul ca ID pentru cele vechi
                                                
                                                return (
                                                    <li key={id || index} className="notification-item">
                                                        <span className="notification-text">{text}</span>
                                                        <button 
                                                            className="delete-notification-btn"
                                                            onClick={(e) => removeNotification(id, e)}
                                                            title="Remove"
                                                        >
                                                            ×
                                                        </button>
                                                    </li>
                                                );
                                            })}
                                        </ul>
                                    ) : (
                                        <p className="no-notifications">No notifications yet.</p>
                                    )}
                                </div>
                            )}
                        </li>
                    )}

                    {!isLoggedIn && <li><Link to="/login">Login</Link></li>}
                    {!isLoggedIn && <li><Link to="/register">Sign Up</Link></li>}
                    {isLoggedIn && <li><button onClick={handleLogout} className="logout-btn">Logout</button></li>}
                </ul>
            </nav>
            
            <main className="main-content">
                <Routes>
                    <Route path="/login" element={<Login onLoginSuccess={handleLoginSuccess} />} />
                    <Route path="/register" element={<Register />} />
                    <Route
                        path="/home"
                        element={
                            <PrivateRoute isLoggedIn={isLoggedIn}>
                                <Home 
                                    stompClient={stompClientRef.current} 
                                    latestNotification={latestNotification}
                                />
                            </PrivateRoute>
                        }
                    />
                    <Route path="/" element={<Navigate to="/login" />} />
                </Routes>
            </main>
        </div>
    );
}

export default App;
