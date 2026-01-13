import React, { useState, useEffect, useRef } from 'react';
import SockJS from 'sockjs-client';
import Stomp from 'stompjs';
import './LiveRecommendations.css'; // Vom crea și un CSS pentru stilizare

const LiveRecommendations = () => {
    const [notifications, setNotifications] = useState([]);
    const stompClientRef = useRef(null);

    useEffect(() => {
        // Conectare la WebSocket
        const socket = new SockJS('http://localhost:8080/ws');
        const stompClient = Stomp.over(socket);
        stompClientRef.current = stompClient;
        
        // Dezactivăm logurile de debug din consolă pentru curățenie
        stompClient.debug = null;

        stompClient.connect({}, () => {
            console.log('LiveRecommendations: Connected to WebSocket');
            
            // Abonare la topicul de alerte
            stompClient.subscribe('/topic/alerts', (message) => {
                if (message.body) {
                    addNotification(message.body);
                }
            });
        }, (error) => {
            console.error('LiveRecommendations: WebSocket connection error:', error);
        });

        return () => {
            if (stompClient && stompClient.connected) {
                stompClient.disconnect();
            }
        };
    }, []);

    const addNotification = (message) => {
        const id = Date.now();
        setNotifications(prev => [...prev, { id, message }]);

        // Auto-remove după 5 secunde
        setTimeout(() => {
            removeNotification(id);
        }, 5000);
    };

    const removeNotification = (id) => {
        setNotifications(prev => prev.filter(n => n.id !== id));
    };

    return (
        <div className="live-recommendations-container">
            {notifications.map(notif => (
                <div key={notif.id} className="live-notification-toast">
                    <span className="notification-icon">🔔</span>
                    <span className="notification-message">{notif.message}</span>
                    <button 
                        className="close-notification" 
                        onClick={() => removeNotification(notif.id)}
                    >
                        ×
                    </button>
                </div>
            ))}
        </div>
    );
};

export default LiveRecommendations;
