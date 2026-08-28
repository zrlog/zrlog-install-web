import {useId} from "react";

type ZrLogMarkProps = {
    size?: number | string;
};

const ZrLogMark = ({size = 36}: ZrLogMarkProps) => {
    const id = useId().replace(/:/g, "");
    const gradientId = `zrlog-mark-gradient-${id}`;
    const clipId = `zrlog-mark-clip-${id}`;

    return <span className="zrlog-mark" style={{width: size, height: size}} aria-hidden="true">
        <svg width={size} height={size} viewBox="0 0 512 512" xmlns="http://www.w3.org/2000/svg">
            <defs>
                <linearGradient id={gradientId} x1="0%" y1="0%" x2="100%" y2="100%">
                    <stop offset="15%" stopColor="rgb(140, 181, 75)"/>
                    <stop offset="70%" stopColor="rgb(255, 193, 7)" stopOpacity="0.9"/>
                </linearGradient>
                <clipPath id={clipId}>
                    <path d="M256,256 L256,0 A200,500 2 1,1 106,423 Z" fill="white"/>
                </clipPath>
            </defs>
            <circle cx="256" cy="256" r="200" stroke="#dce4e8" strokeWidth="50" fill="none"/>
            <circle cx="256" cy="256" r="200" stroke={`url(#${gradientId})`} strokeWidth="50"
                    fill="none" clipPath={`url(#${clipId})`}/>
            <g transform="rotate(45,256,256)">
                <rect x="232" y="140" width="48" height="210" fill="#ffc107"/>
                <polygon points="232,350 280,350 256,390" fill="#ddaa77"/>
                <polygon points="251,380 261,380 256,398" fill="#6e6e6e"/>
                <rect x="232" y="110" width="48" height="30" fill="#ff6b6b"/>
                <rect x="232" y="140" width="48" height="15" fill="#b0b0b0"/>
            </g>
        </svg>
    </span>;
};

export default ZrLogMark;
