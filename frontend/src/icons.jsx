export function Icon({name, className}) {
    const paths = {
        lock: <><path d="M8 11V8a4 4 0 0 1 8 0v3"/><rect x="5" y="11" width="14" height="10" rx="3"/><path d="M12 15v2"/></>,
        shield: <><path d="M12 3l8 4v5c0 5-3.4 8.3-8 9-4.6-.7-8-4-8-9V7l8-4z"/><path d="M9 12l2 2 4-4"/></>,
        arrow: <><path d="M4 12h16M12 4l8 8-8 8"/></>,
        chart: <><path d="M5 20h14M7 17V9m5 8V4m5 13v-5"/></>,
        eye: <><path d="M2 12s3.5-6 10-6 10 6 10 6-3.5 6-10 6S2 12 2 12z"/><circle cx="12" cy="12" r="3"/></>,
        eyeOff: <><path d="M3 3l18 18M10.6 6.2A10 10 0 0 1 12 6c6.5 0 10 6 10 6a18 18 0 0 1-2.1 2.8M6.3 6.3C3.5 8.2 2 12 2 12s3.5 6 10 6a10 10 0 0 0 3.7-.7M9.9 9.9a3 3 0 0 0 4.2 4.2"/></>,
        home: <><path d="M3 8l9-5 9 5v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8z"/><path d="M9 21v-8h6v8"/></>,
        clock: <><path d="M12 8v5l3 2"/><circle cx="12" cy="12" r="9"/></>,
        trash: <><path d="M3 6h18M8 6V4h8v2M6 6l1 15h10l1-15M10 10v7M14 10v7"/></>,
        logout: <><path d="M10 5H5v14h5M14 8l4 4-4 4M8 12h10"/></>,
        menu: <path d="M4 7h16M4 12h16M4 17h16"/>,
        upload: <><path d="M12 16V4M7 9l5-5 5 5M5 20h14"/></>,
        search: <><circle cx="11" cy="11" r="7"/><path d="M20 20l-4-4"/></>,
        share: <><circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/><path d="M8.6 10.5l6.8-4M8.6 13.5l6.8 4"/></>,
        download: <><path d="M12 4v12M7 11l5 5 5-5M5 20h14"/></>,
        close: <path d="M6 6l12 12M18 6L6 18"/>
    };

    return <svg className={className} viewBox="0 0 24 24" aria-hidden="true">{paths[name]}</svg>;
}

export function Brand() {
    return (
        <span className="brand">
            <span className="brand-mark"><Icon name="lock"/></span>
            <span>CloudVault</span>
        </span>
    );
}
