(() => {
  const originalFetch = window.fetch;
  window.fetch = async function(input, init) {
    const resp = await originalFetch(input, init);
    try {
      const cloned = resp.clone();
      const ct = cloned.headers.get('content-type') || '';
      if (resp.status === 401) {
        alert('Your session has expired. Redirecting to the login page.');
        window.location.href = '/login';
      } else if (ct.includes('application/json')) {
        const data = await cloned.json();
        // Fallback: some responses carry a 401 in the JSON body without a 401 HTTP status.
        // Match the backend GlobalExceptionHandler payload: { error: 'Not logged in', status: 401 }.
        if (data?.status === 401 && data?.error === 'Not logged in') {
          alert('Your session has expired. Redirecting to the login page.');
          window.location.href = '/login';
        }
      }
    } catch (e) {}
    return resp;
  };
})();
