import { ZrLogMark as SharedZrLogMark } from "@zrlog/ui/material";

const ZrLogMark = ({size = 36}: {size?: number | string}) =>
    <SharedZrLogMark size={size} className="zrlog-mark"/>;
export default ZrLogMark;
