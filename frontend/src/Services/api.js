import { safeErrorData } from '../utils/messages.js';

import axios from "axios";

const API = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL || "http://localhost:8080/api/v1",
    headers: {
        "Content-Type": "application/json",
        "X-Zaylope-Request": "1",
    },
    withCredentials: true,
    timeout: 15000,
});

API.interceptors.response.use(response => response, error => {
    if (error.response) error.response.data = safeErrorData(error.response.data, error.response.status);
    error.message = error.response ? error.response.data.detail : 'Please check your internet connection and try again.';
    return Promise.reject(error);
});
export default API;


