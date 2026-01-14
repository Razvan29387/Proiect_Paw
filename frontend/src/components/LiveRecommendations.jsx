import React, { useState, useEffect } from 'react';
import './LiveRecommendations.css';

const LiveRecommendations = ({ newNotification }) => {
    const [notifications, setNotifications] = useState([]);

    useEffect(() => {
        if (newNotification) {
            addNotification(newNotification.text);
        }
    }, [newNotification]);

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
